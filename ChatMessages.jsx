import React, { useState, useEffect, useContext } from 'react';
import { AuthContext } from './../../shared/context/Auth';
import { UserContext } from '../../shared/context/User';
import CustomMarkdown from './CustomMarkdown';
import SummaryTable from '../table/Table';
import LoadingMessages from '../loading/LoadingMessages';
import useTheme from '../../hooks/useTheme';
import SeverityTabs from './Tab';

const ChatMessages = ({
  analysis,
  aid,
  codingTips,
  severityLevels,
  selectedTab,
  severityCounts,
  setSelectedTab,
  severityMap,
  analysisHistory,
  processAnalysis,
  extractedSections,
  setExtractedSections
}) => {
  const [val, setVal] = useState('');
  const { theme } = useTheme();
  const { isAuth } = useContext(AuthContext);
  const { name: user } = useContext(UserContext);

  useEffect(() => {
    setVal(user ? user : 'User');
  }, [user, analysis]);

  if (!isAuth) {
    return null;
  }

  const language = analysis?.language;

  useEffect(() => {
    processAnalysis(analysis, setExtractedSections);
  }, [analysis]);

  useEffect(() => {
    if (!aid) {
      setExtractedSections([]);
    }
  }, [aid]);

  return (
    <div className="pb-10 text-[#323130] dark:text-[#D8D8DA]">
      <div className="message-details text-left flex flex-col gap-5">
        <div className="message-item text-[16px] md1:text-[24px] flex flex-col md1:flex-row md1:gap-2">
          <span className="font-bold">
            {analysis.factor === 'unittestability'
              ? 'Unit Testability'
              : analysis.factor === 'power_analysis'
                ? 'Power Analysis'
                : analysis.factor.charAt(0).toUpperCase() +
                  analysis.factor.slice(1)}
          </span>

          <span className="text-[#323130] flex gap-2 dark:text-[#d8d8da]">
            <span className="text-[#959491] hidden md1:block">|</span>
            {analysis.fileName}
          </span>
        </div>

        <div className="flex flex-col md1:flex-row items-start">
          <div className="sm:max-w-[100%] w-[90vw] flex flex-col gap-5">
            <p className="text-[16px] md1:text-[24px] font-bold">
              Analysis Summary
            </p>

            <SeverityTabs
              analysis={analysis}
              analysisHistory={analysisHistory}
              severityLevels={severityLevels}
              severityCounts={severityCounts}
              selectedTab={selectedTab}
              setSelectedTab={setSelectedTab}
              severityMap={severityMap}
            />

            {analysis.running &&
            Object.values(severityCounts).every((count) => count === 0) ? (
              <div className="text-[20px] mt-10">
                <LoadingMessages codingTips={codingTips} />
              </div>
            ) : (
              <>
                <SummaryTable
                  analysis={analysis}
                  extractedSections={extractedSections}
                />

                {analysis?.error ? (
                  <div className="text-[20px] mt-10">{analysis?.error}</div>
                ) : (
                  <>
                    {analysis?.response?.map((item, index) => (
                      <div key={index} className="my-5 max-w-[100%] w-[100%]">
                        <h3 className="text-[16px] sm:text-[24px] font-bold">
                          {item?.characteristic}
                        </h3>
                        <p className="text-[14px] sm:text-[20px]">
                          {item?.description_of_characteristic}
                        </p>

                        {item?.issue_items?.map((issue, issueIndex) => (
                          <div
                            key={issueIndex}
                            className="text-[14px] sm:text-[20px] flex flex-col gap-4 my-5"
                          >
                            <div>
                              <p
                                className="text-[14px] sm:text-[22px] font-bold"
                                id={issue?.uid}
                              >
                                {issue?.uid}
                              </p>
                            </div>
                            <div>
                              <p className="text-[14px] sm:text-[22px] font-bold">
                                Issue
                              </p>
                              <p>{issue?.issue}</p>
                            </div>
                            <div>
                              <CustomMarkdown copyButton={true}>
                                {issue.issue_code_snippet &&
                                  `\`\`\`${language || ''}\n${issue.issue_code_snippet}\n\`\`\``}
                              </CustomMarkdown>
                            </div>
                            <div>
                              <p className="text-[14px] sm:text-[22px] font-bold">
                                Solution
                              </p>
                              <p>{issue?.solution}</p>
                              <CustomMarkdown copyButton={true}>
                                {issue.solution_code_snippet &&
                                  `\`\`\`${language || ''}\n${issue.solution_code_snippet}\n\`\`\``}
                              </CustomMarkdown>
                            </div>
                          </div>
                        ))}
                      </div>
                    ))}
                  </>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ChatMessages;
