from typing import Any, Dict, List, Optional, Union
from app.config import MIN_ISSUE_LIMIT
from app.services.data import Factors, char_factor_map, OWASP_GROUPS
from app.dependencies import logger
import json


class PromptService:
    def __init__(self):
        """
        Initialize the service with predefined prompts.
        """
        self.prompts = {
            "factor_analysis_prompt": self._factor_analysis_prompt,
            "applicability_check_prompt": self._applicability_check_prompt,
            "applicability_check_small_prompt": self._applicability_check_small_prompt,
            "md_to_json_prompt": self._md_to_json_prompt,
            "power_analysis_prompt": self._power_analysis_prompt,
            "power_analysis_small_prompt": self._power_analysis_small_prompt,
            "owasp_analysis_prompt": self._owasp_analysis_prompt,
            "generate_pr_summary": self.generate_pr_summary,
        }

    async def get_prompt(
        self, prompt_name: str, **kwargs: Any
    ) -> Union[str, Dict[str, str]]:
        """
        Retrieve a prompt by its name and process it with parameters if required.

        :param prompt_name: The name of the prompt to retrieve.
        :param kwargs: Parameters for the prompt, if applicable.
        :return: The processed prompt, which could be a string or a list of strings.
        """
        if prompt_name not in self.prompts:
            raise Exception(f"Prompt '{prompt_name}' does not exist.")

        # Call the appropriate prompt handler
        return await self.prompts[prompt_name](**kwargs)

    async def _applicability_check_prompt(self, code: str, factor: str) -> str:
        """
        Return the applicability check prompt.
        :param code: Code to be analyzed.
        :param factor: The factor of analysis. A valid factor must exist in the Factors mapping.
        :raises ValueError: If the provided factor does not exist in the Factors mapping.
        :return: A formatted applicability check prompt.
        """

        if factor not in Factors:
            raise ValueError(f"Factor '{factor}' is not defined in Factors mapping.")
        char_objects = Factors[factor]
        char_lines = [f"- {obj['characteristic']} ({obj['description']})" for obj in char_objects]
        chars = "\n".join(char_lines) + "\n"

        return f"""
I am performing code analysis on a given code file using a set of characteristics on the aspect of {factor}. Before proceeding with the characteristic analysis, please determine the programming language of the code file. Once the language has been identified, assess whether each characteristic is applicable to the code, and if applicable, determine whether it requires changes.

For each characteristic:
- Indicate if it is applicable (i.e., if the characteristic is relevant for evaluating the given code).
- Indicate if it requires changes (i.e., if modifications are needed in the code to address the characteristic).
- Provide a reason for your decision for each field.

Code:
{code}

Characteristics:
{chars}

Output Format: Please provide the output in the following JSON format without backticks:
{{
    "language": "<Identified programming language>",
    "characteristics": [
        {{
            "name": "<Characteristic 1>",
            "applicable": <true/false>,
            "require_changes": <true/false>,
            "reason": "<Explanation for why the characteristic is applicable or not and why changes are or aren't needed>"
        }},
        {{
            "name": "<Characteristic 2>",
            "applicable": <true/false>,
            "require_changes": <true/false>,
            "reason": "<Explanation for why the characteristic is applicable or not and why changes are or aren't needed>"
        }},
        ...
    ]
}}
"""

    async def _applicability_check_small_prompt(self, code: str, factor: str) -> str:
        """
        Return the applicability check prompt.
        :param code: Code to be analyzed.
        :param factor: The factor of analysis.
        :return: A formatted applicability check prompt.
        """

        return f"""
I am performing code analysis on a given code file. Before proceeding with any further analysis, please determine the **programming language** of the code.

**Code:**
{code}

**Output Format:** Please provide the output in the following JSON format without backticks:
{{
    "language": "<Identified programming language>"
}}
"""

    async def _md_to_json_prompt(self, md: str) -> str:
        return f"""
Below is a markdown structured in the format:
- # Name of characteristic
- A concise explanation of the characteristic.

List of Issues in the format:
- Issue: (Description of the identified issue.)
- Identifier for Issue (an abbreviation).
- Severity: ([Critical, High, Medium, Low])
- (The code snippet where the issue is found)
- Solution: (Description of the solution.)
- (Improved code snippet that resolves the issue.)

You need to convert this markdown into structured JSON output as given below:
```json
{{
  "characteristic": "string",  // just write the name of the characteristic.
  "description_of_characteristic": "string", // concise explanation of the characteristic.
  "issue_items": [
    {{
      "id": "string",  // Unique identifier for the issue (the abbreviation).
      "issue": "string",  // description of the identified issue.
      "issue_code_snippet": "string",  // the code snippet where the issue is found, formatted with proper indentation and line breaks.
      "severity": "string",  // The severity of the issue. choose from: [Critical, High, Medium, or Low].
      "solution": "string",  // Description of the solution.
      "solution_code_snippet": "string"  // The improved code snippet that resolves the issue, formatted with proper indentation and line breaks.
    }}
  ]
}}

Markdown:
{md}
"""

    async def _factor_analysis_prompt(
        self, factor_name: str, applicable_chars: List[str] = None
    ) -> Dict[str, str]:
        """
        Generate an array of prompts for the factor.

        :param factor_name: The name of the factor.
        :param applicable_chars: Characteristics to filter.
        :return: A dictionary of prompts keyed by characteristic.
        """
        prompt_dict = {}
        char_objects = Factors[factor_name]

        if applicable_chars:
            filtered_char_objects = [
                obj for obj in char_objects if obj["characteristic"] in applicable_chars
            ]
        else:
            filtered_char_objects = char_objects

        results = await self._process_factors(factor_name, filtered_char_objects)

        for characteristic, prompt in results:
            prompt_dict[characteristic] = prompt

        return prompt_dict

    async def _process_factors(
        self, factor_name: str, char_objects: List[Dict[str, Any]]
    ) -> List[tuple]:
        """
        Process a list of factors to generate prompts.

        :param factor_name: The name of the factor.
        :param char_objects: A list of factor objects to process.
        :return: A list of tuples containing characteristic names and their corresponding prompt templates.
        """
        results = []
        for obj in char_objects:
            characteristic = obj["characteristic"]
            description = obj["description"]
            abbreviation = obj["abbreviation"]
            example = obj["example"]

            # Generate the prompt for each factor object
            prompt_template = f"""
Consider yourself a senior software engineer. Perform a code analysis on the given code, focusing on {characteristic}.
{description}

CRITICAL POINT : Every identified Issue must follow the structure below in attached example only. Do not skip, or omit any part.

1. Thorough Examination: Before presenting the analysis, examine the entire code carefully. Take your time to understand the context and functionality.

2. Relevant Issues Only: Identify issues strictly related to {characteristic} that have a real impact on the code's quality or functionality.

3. No Forced Issues: Avoid creating issues that do not exist. Give code examples from existing code instead of generic examples from outside the given code set.

4. Detailed Solutions: For each identified issue, provide a clear and detailed solution with code snippets. Ensure that the solution code snippet is an improved version that resolves the identified issue and is not a repetition of the original code snippet.

5. Sequential Numbering: For each issue, create an alphanumeric number starting with the first three letters of {characteristic} and append to that a number with a hyphen, starting from 100 and increasing sequentially for each issue. Numbering should start from a new line.

6. Plain Text Response: Only apply the H2, H3 tags for the response. Dont't forget to add the hashes for H2 H3.

## Format of output:

## A concise explanation of what {characteristic} is and its significance in software {factor_name}.
### Issue: (Description of the identified issue.)
### {abbreviation} (or the appropriate numbering).
### Severity: (judge the severity of issue: [Critical, High, Medium, Low].)
(The code snippet where the issue is found)
### Solution: (description of the solution.)
(If applicable, provide an improved code snippet that resolves the issue.)

## Examples
{example}

[Note: Please follow the example format given consistently. This example is provided to illustrate the expected format only.]
"""
            results.append((characteristic, prompt_template))
        return results

    async def _power_analysis_prompt(
        self, factor_name: str, context: str, applicable_chars: List[str] = None, additional_instructions: str = None, pr_summary = None
    ) -> Dict[str, str]:
        """
        Generate an array of prompts for the power analysis.

        :param factor_name: The name of the factor.
        :param applicable_chars: Characteristics to filter.
        :return: A dictionary of prompts keyed by characteristic.
        """
        prompt_dict = {}
        try:
            char_objects = Factors[factor_name]

            if applicable_chars:
                filtered_char_objects = [
                    obj for obj in char_objects if obj["characteristic"] in applicable_chars
                ]
            else:
                filtered_char_objects = char_objects

            # add a check if addiional instructions has spaces
            if (
                isinstance(additional_instructions, str)
                and additional_instructions.strip()
            ):
                additional_characteristic = await self._create_custom_characteristic(user_instructions=additional_instructions)
                filtered_char_objects.append(additional_characteristic)

            results = await self._process_power_analysis_factor(
                factor_name, context, filtered_char_objects, pr_summary
            )

            for characteristic, prompt in results:
                prompt_dict[characteristic] = prompt

            return prompt_dict
        
        except Exception as e:
            # Handle any unexpected errors
            logger.error(f"Unexpected error in _power_analysis_prompt: {str(e)}")
            return {}

    async def _create_custom_characteristic(self, user_instructions: str):
        return {
            "characteristic": "Additional User Instructions",
            "description": f"Check for the following user-defined instructions strictly in the code. If found, point out those issues. If not, you can respond with \"No issues found\". Only check for user instructions and nothing else. Do not check for any other issues. \n\n Additional User Instructions:\n{user_instructions}",
            "abbreviation": "AUI",
            "weight": 2,
            "example": "## Code Hygiene ensures the code is clean and production-ready.\n\n ### Issue: Console logging left in production.\n\n ### AUI-100\n\n ### Severity: Medium\n\n```js\nconsole.log('Debug info');\n```\n\n ### Solution: Remove or replace with proper logging.\n\n```js\n// logger.debug('Debug info');\n```"
        }

    async def _process_power_analysis_factor(
        self, factor_name: str, context: str, char_objects: List[Dict[str, Any]], pr_summary=None
    ) -> List[tuple]:
        """
        Process a list of characteristics to generate prompts.

        :param factor_name: The name of the factor.
        :param char_objects: A list of factor objects to process.
        :return: A list of tuples containing characteristic names and their corresponding prompt templates.
        """
        results = []
        for obj in char_objects:
            characteristic = obj["characteristic"]
            description = obj["description"]
            abbreviation = obj["abbreviation"]
            example = obj["example"]
            weight = obj["weight"]

            # Add PR summary line if available
            pr_line = (
                f"\n\nAlso, Here is a concise summary of the PR, providing context for the file (which is part of this PR) that you are going to analyze: \n{pr_summary}\n"
                if pr_summary
                else ""
            )

            # Generate the prompt for each factor object
            prompt_template = f"""
Consider yourself a senior software engineer. Perform a detailed code analysis on the above given code, focusing on {characteristic}. {description}. {pr_line}

### **Analysis Guidelines:**

1. **Thorough Examination:**  
   - Examine only the **Code To Anlayze** and fully understand its functionality, constraints, and intended behavior.  

2. **Relevant Issues Only:**  
   - Identify maximum {weight} critical or high issues. Avoid speculative, trivial, or low-impact concerns. Focus only on problems that can cause critical to high damage or consequences.
   - Consider how the provided **Context** influences issue identification and solutions.  

3. **No Forced Issues:**  
   - Avoid creating issues that do not exist. Always provide examples **directly from the given code** instead of generic examples.  

4. **Detailed Solutions:**  
   - For each identified issue, offer a **clear and detailed solution** with an improved code snippet. Ensure the solution is an enhancement, not a repetition.  

5. **Sequential Numbering:**  
   - Each issue should follow a **structured numbering format**, starting with the first three letters of {characteristic} and appending a sequential number (e.g., **{abbreviation}**).  

6. **Plain Text Response Format:**  
   - Use **H2 (`##`) and H3 (`###`) headings** to structure your response.  
   - Do **not** use any other formatting (e.g., tables, markdown lists, or bullet points).  

### **Format of Output:**  

## A concise explanation of what {characteristic} is and its significance.
### Issue: (Description of the identified issue.)
### {abbreviation} (or the appropriate numbering).
### Severity: (judge the severity of issue: [Critical, High, Medium, Low].)
(The code snippet where the issue is found)
### Solution: (description of the solution.)
(If applicable, provide an improved code snippet that resolves the issue.)

## Examples
{example}

[**Note:** Follow the response structure given in the example strictly.]

## **Context:** Use the **context only for understanding the code snippet** if required.  
   - **DO NOT analyze, critique, or suggest improvements for the context itself.**  
   - Use the context **ONLY** to interpret the behavior, dependencies, or functionality of the provided code snippet.  
   - {context}

"""
            results.append((characteristic, prompt_template))
        return results

    async def _power_analysis_small_prompt(
        self,
        factor_name: str,
        context: str,
        additional_instructions: str = None
    ) -> Dict[str, str]:
        """
        Generate an array of prompts for the power analysis.

        :param factor_name: The name of the factor.
        :param applicable_chars: Characteristics to filter.
        :return: A dictionary of prompts keyed by characteristic.
        """
        prompt_dict = {}
        additional_characteristic = None

        # add a check if addiional instructions has spaces
        if (
            isinstance(additional_instructions, str)
            and additional_instructions.strip()
        ):
            additional_characteristic = await self._create_custom_characteristic(user_instructions=additional_instructions)

        results = await self._power_analysis_small(factor_name, context, additional_characteristic)

        for characteristic, prompt in results:
            prompt_dict[characteristic] = prompt

        return prompt_dict

    async def _power_analysis_small(
        self, factor_name: str, context: str, additional_characteristic: Optional[Dict[str, Any]] = None
    ) -> List[tuple]:
        """
        Process a list of characteristics to generate prompts.

        :param factor_name: The name of the factor.
        :param char_objects: A list of factor objects to process.
        :return: A list of tuples containing characteristic names and their corresponding prompt templates.
        """
        char_objects = Factors[factor_name]

        if additional_characteristic:
            char_objects.append(additional_characteristic)

        results = []
        issue_limit = MIN_ISSUE_LIMIT

        for obj in char_objects:
            characteristic = obj["characteristic"]
            description = obj["description"]
            abbreviation = obj["abbreviation"]
            example = obj["example"]

            # Generate the prompt for each factor object
            prompt_template = f"""
Consider yourself a senior software engineer. Perform a **detailed code analysis** on the above given **Code To Anlayze**
Focusing on identifying maximum {issue_limit} key issues related to readability, maintainability, and naming convention improvements. Identify maximum {issue_limit} critical runtime issues that have a significant impact or damage. Remember Do Not analyze the context code that is just for your understanding only.

### **Analysis Guidelines:**

1. **Thorough Examination:**  
   - Examine only the **code** and fully understand its functionality, constraints, and intended behavior.  

2. **Detailed and Clear Explanations:**  
    -  For each identified issue, provide a **detailed explanation**.
    -  For each proposed solution
    -  Provide a clear and **detailed explanation** that describes the problem.

3. **No Forced Issues:**  
   - If no significant issues are identified in the provided code snippet, do not analyze the context code to create issues forcefully. Only report valid concerns directly related to the provided code snippet. 

4. **Sequential Numbering:**  
   - Each issue should follow a **structured numbering format**, starting with the first three letters of {characteristic} and appending a sequential number (e.g., **{abbreviation}**). 

5. **Plain Text Response Format:**  
   - Use **H2 (`##`) and H3 (`###`) headings** to structure your response.  
   - Do **not** use any other formatting (e.g., tables, markdown lists, or bullet points).  

### **Format of Output:**  

##  {description}.
### Issue: (Description of the issue.)
### {abbreviation} (or the appropriate numbering).
### Severity: (judge the severity of issue: [Critical, High, Medium, Low].)
(The code snippet where the issue is found)
### Solution: (Description  of the solution.)
(If applicable, provide an improved code snippet that resolves the issue.)

## Examples
{example}

## **Note:** 
    - Follow the response structure given in the example strictly.
    - ***Issue description and Solution description shoul'd not be short it should be long and descriptive.*** 

## **Context:** Use the **context only for understanding the code snippet** if required.  
   - **DO NOT analyze, critique, or suggest improvements for the context itself.**  
   - Use the context **ONLY** to interpret the behavior, dependencies, or functionality of the provided code snippet.  
   - {context}

"""
            results.append((characteristic, prompt_template))
        return results

    async def _owasp_analysis_prompt(
        self, factor_name: str, context: str, applicable_chars: List[str] = None, pr_summary = None
    ) -> Dict[str, str]:
        """
        Generate an array of prompts for the owasp analysis.

        :param factor_name: The name of the factor.
        :param applicable_chars: Characteristics to filter.
        :return: A dictionary of prompts keyed by characteristic.
        """
        prompt_dict = {}
        char_objects = Factors[factor_name]

        if applicable_chars:
            filtered_char_objects = [
                obj for obj in char_objects if obj["characteristic"] in applicable_chars
            ]
        else:
            filtered_char_objects = char_objects

        # **Group applicable characteristics**
        grouped_characteristics = {}
        for group_name, characteristics in OWASP_GROUPS.items():
            group_filtered = [
                obj
                for obj in filtered_char_objects
                if obj["characteristic"] in characteristics
            ]
            if group_filtered:  # Only add groups that have applicable characteristics
                grouped_characteristics[group_name] = group_filtered

        for group_name, char_list in grouped_characteristics.items():
            results = await self._process_owasp_analysis_factor(
                factor_name, char_list, group_name, context, pr_summary
            )
            prompt_dict[group_name] = results

        return prompt_dict

    async def _process_owasp_analysis_factor(
        self,
        factor_name: str,
        char_objects: List[Dict[str, Any]],
        group_name: str,
        context: str = "",
        pr_summary = None
    ) -> str:
        """
        Process a list of characteristics within a specific OWASP group.

        :param factor_name: The name of the factor.
        :param context: The code context.
        :param char_objects: A list of factor objects to process.
        :param group_name: The OWASP group this belongs to.
        :return: A formatted prompt string.
        """
        characteristics_list = [obj["characteristic"] for obj in char_objects]
        abbreviation_list = [obj["abbreviation"] for obj in char_objects]
        example_list = [obj["example"] for obj in char_objects]
        context_section = (
            f"2. **Context:** Additional code from the other files which are imported.\n{context}\n"
            if context
            else ""
        )

        # Add PR summary line if available
        pr_line = (
            f"\n\nHere is a concise summary of the PR, providing context for the file (which is part of this PR) that you are going to analyze: \n{pr_summary}\n"
            if pr_summary
            else ""
        )

        # Construct a single prompt for the entire group
        prompt_template = f"""
Consider yourself a senior security engineer capable of performing security analysis. 
Your task is to analyze the provided code for vulnerabilities related to the following OWASP risks:

{', '.join(characteristics_list)}

{pr_line}

### **Input Structure:**  
1. **Code:** The provided code to analyze.
{context_section}

### **Guidelines:**  
1️. **Prioritize Higher-Ranked OWASP Risks:**  
   - Focus **70% of the analysis effort** on the **highest-ranked OWASP vulnerabilities** in this set.  
   - Lower-ranked risks should only be analyzed if they contain **critical security concerns**.  
2. **Thorough Examination:** Review the full code, with referencing the context if provided, before providing the analysis.  
3. **Relevant Issues Only:** Identify only security issues **directly related** to the above OWASP risks.  
4. **No Redundant Issues:**  
   - If an issue affects multiple risks within this group, report it **under the most relevant category only** and **do not duplicate it**.   
5. **Detailed Solutions with Code Fixes:** Ensure the **improved code snippet fully resolves the issue**.  
6. **Plain Text Response Format:**  
   - Use ** H1 (`#`) H2 (`##`) and H3 (`###`) headings** to structure your response.  
   - Do **not** use any other formatting (e.g., tables, markdown lists, or bullet points). 
7. 7. **Sequential Numbering:**  
   - Each issue should use a **structured numbering format**, starting with the OWASP abbreviation and a sequential number (e.g., `A01-100`).

## **Output Format:**  
# (OWASP Characteristic Name with Code) (e.g. A01:2021 Broken Access Control)
## (A concise explanation of what the characteristic is and its significance.)
### Issue: (Description of the vulnerability.)  
### (appropriate abbreviation based on the characteristic, with a sequesntial number starting from 100) (e.g. A01-100)
### Severity: (Critical, High, Medium, Low)  
(The code snippet where the issue is found.)  
### Solution: (Description of the fix.)  
(Improved code snippet resolving the issue.)  
"""
        return prompt_template

    async def generate_pr_summary(self, pr_full_data: Dict[str, Any], pr_file_details: List[Dict[str, Any]]) -> str:
        """
        Generates a PR summary using AI by combining high-level PR metadata and file-level changes.
        """

        # --- Step 1: Extract high-level info ---
        title = pr_full_data.get("title", "")
        description = pr_full_data.get("body", "")  # GitHub API uses 'body' not 'description'
        author = pr_full_data.get("user", {}).get("login", "unknown")
        labels = [label.get("name", "") for label in pr_full_data.get("labels", [])]
        commits = [commit.get("commit", {}).get("message", "") for commit in pr_full_data.get("commits", [])]

        # --- Step 2: Extract file-level info ---
        file_summaries = []
        for f in pr_file_details:
            file_summary = {
                "filename": f.get("filename", ""),
                "status": f.get("status", ""),
                "additions": f.get("additions", 0),
                "deletions": f.get("deletions", 0),
                "changes": f.get("changes", 0),
                "patch": f.get("patch", "")
            }
            file_summaries.append(file_summary)

        # --- Step 3: Build prompt for AI ---
        prompt = f"""
    You are an expert code reviewer. Summarize the Pull Request in a **concise, generic, and high-level way** so that it’s easy for any reviewer to quickly understand the overall impact.

    ### PR Metadata
    - **Title:** {title}
    - **Description:** {description}
    - **Author:** {author}
    - **Labels:** {', '.join(labels) if labels else 'None'}
    - **Commits:** {len(commits)} commits
    {chr(20).join([f"  - {msg}" for msg in commits])}

    ### File Changes (metadata + partial patch for context)
    {json.dumps(file_summaries, indent=2)}

    ---

    ### Response Format
    ## PR Summary
    - Provide a **broad overview** of what this PR does (new features, fixes, refactors, optimizations, etc.).  
    - Focus on the **overall purpose and impact** rather than file-by-file details.  
    - Highlight any potential implications of the changes, such as improvements to performance, security, or the user experience, if applicable.  
    - Keep the explanation objective, clear, and concise.

    ## File-wise Changes
    Provide a table that explains **what changed in each file and why**, along with the **impact** of those changes.  
    Avoid listing just line additions/removals. Instead, focus on the logic, purpose, and consequences.

    | File Name   | Summary of Modifications |
    |-------------|---------------------------------------|
    | file1.py    | Refactored authentication logic to use token-based validation, improving security and reducing dependency on sessions. |
    | file2.js    | Updated UI form validation to handle edge cases, enhancing user experience and reducing input errors. |
"""

        return prompt