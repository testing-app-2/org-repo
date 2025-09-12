import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

public class InsecureLoginTest {

    public static void main(String[] args) {
        WebDriver driver = null;
        try {
            // CWE-259: Hardcoded credentials
            String username = "admin";
            String password = "password123";

            // CWE-73: Driver path taken from environment variable (unvalidated external control)
            String driverPath = System.getenv("CHROME_DRIVER");
            System.setProperty("webdriver.chrome.driver", driverPath);

            driver = new ChromeDriver();

            // CWE-477: Use of obsolete methods (implicit waits instead of explicit waits)
            driver.manage().timeouts().implicitlyWait(20, java.util.concurrent.TimeUnit.SECONDS);

            // CWE-601: URL redirection vulnerability (no validation on URL)
            driver.get("http://insecure-example.com/login");

            // CWE-285: Improper authorization check (assuming elements are accessible)
            WebElement userField = driver.findElement(By.id("username"));
            WebElement passField = driver.findElement(By.id("password"));
            WebElement loginBtn = driver.findElement(By.id("loginBtn"));

            // CWE-319: Sensitive data in cleartext
            userField.sendKeys(username);
            passField.sendKeys(password);

            loginBtn.click();

            // CWE-209: Printing detailed exception/stack trace to console
            System.out.println("Login attempted with username: " + username + " and password: " + password);

            if (driver.getTitle().contains("Dashboard")) {
                System.out.println("Access granted!");
            } else {
                System.out.println("Access denied!");
            }
        } catch (Exception e) {
            // CWE-209: Revealing stack trace
            e.printStackTrace();
        } finally {
            // CWE-404: No proper resource cleanup (driver might remain open if null or crash before quit)
            //this will close the browser in focus
            driver.quit();
        }
    }
}
