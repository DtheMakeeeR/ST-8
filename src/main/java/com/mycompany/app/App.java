package com.mycompany.app;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App {
  public static void main(String[] args) {
    System.setProperty("webdriver.chrome.driver", "C:\\ChromeDriver\\chromedriver-win64\\chromedriver.exe");

    Path resultDir = Paths.get("result");
    Path dataFile = Paths.get("data", "data.txt");

    try {
      Files.createDirectories(resultDir);

      List<String> lines = Files.readAllLines(dataFile);
      String artist = lines.get(0).trim();
      String title = lines.get(1).trim();

      List<String> tracks = new ArrayList<>();
      for (int i = 2; i < lines.size(); i++) {
        String line = lines.get(i).trim();
        if (!line.isEmpty()) {
          tracks.add(line);
        }
      }

      ChromeOptions options = new ChromeOptions();
      options.addArguments("--remote-allow-origins=*");

      Map<String, Object> prefs = new HashMap<>();
      prefs.put("download.default_directory", resultDir.toAbsolutePath().toString());
      prefs.put("download.prompt_for_download", false);
      prefs.put("plugins.always_open_pdf_externally", true);
      options.setExperimentalOption("prefs", prefs);

      WebDriver driver = new ChromeDriver(options);
      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

      try {
        driver.get("http://www.papercdcase.com/");
        Thread.sleep(2000);

        WebElement artistField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[1]/td[2]/input")
        ));
        artistField.clear();
        artistField.sendKeys(artist);
        System.out.println("Artist: " + artist);

        WebElement titleField = driver.findElement(
                By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[2]/td[2]/input")
        );
        titleField.clear();
        titleField.sendKeys(title);
        System.out.println("Title: " + title);

        for (int i = 0; i < tracks.size() && i < 16; i++) {
          int col = (i < 8) ? 1 : 2;
          int row = (i < 8) ? (i + 1) : (i - 7);
          String trackXpath = String.format(
                  "/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[3]/td[2]/table/tbody/tr/td[%d]/table/tbody/tr[%d]/td[2]/input",
                  col, row
          );
          WebElement trackField = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(trackXpath)));
          trackField.clear();
          trackField.sendKeys(tracks.get(i));
          System.out.println("Track " + (i+1) + ": " + tracks.get(i));
        }

        WebElement jewelCase = driver.findElement(
                By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[4]/td[2]/input[2]")
        );
        jewelCase.click();
        System.out.println("Jewel Case selected");

        WebElement a4 = driver.findElement(
                By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[5]/td[2]/input[2]")
        );
        a4.click();
        System.out.println("A4 selected");

        WebElement submitButton = driver.findElement(
                By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/p/input")
        );
        submitButton.click();
        System.out.println("Form submitted, waiting for PDF...");

        Thread.sleep(8000);

        String currentUrl = driver.getCurrentUrl();
        System.out.println("Current URL: " + currentUrl);

        if (currentUrl.contains(".pdf")) {
          try (InputStream in = URI.create(currentUrl).toURL().openStream()) {
            Files.copy(in, resultDir.resolve("cd.pdf"), StandardCopyOption.REPLACE_EXISTING);
          }
          System.out.println("PDF saved to result/cd.pdf");
        } else {
          List<WebElement> pdfLinks = driver.findElements(By.xpath("//a[contains(@href, '.pdf')]"));
          if (!pdfLinks.isEmpty()) {
            String pdfUrl = pdfLinks.get(0).getAttribute("href");
            try (InputStream in = URI.create(pdfUrl).toURL().openStream()) {
              Files.copy(in, resultDir.resolve("cd.pdf"), StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("PDF saved from link to result/cd.pdf");
          } else {
            System.out.println("No PDF found. Check browser manually.");
          }
        }

      } finally {
        Thread.sleep(3000);
        driver.quit();
      }

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}