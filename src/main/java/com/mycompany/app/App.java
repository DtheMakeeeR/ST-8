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
import java.util.Set;

public class App {

  private static final String BASE_URL = "http://www.papercdcase.com/";
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final int MAX_TRACKS = 16;
  private static final int TRACKS_PER_COLUMN = 8;

  private WebDriver driver;
  private WebDriverWait wait;
  private Path resultDirectory;

  public static void main(String[] args) {
    App app = new App();
    app.execute();
  }

  public void execute() {
    try {
      initializeDriver();
      prepareDirectories();

      FormData formData = loadFormDataFromFile(Paths.get("data", "data.txt"));
      printSummary(formData);

      navigateToSite();
      fillArtistAndTitle(formData.getArtist(), formData.getTitle());
      fillTracks(formData.getTracks());
      selectJewelCaseOption();
      selectA4PaperOption();

      // ГЛАВНОЕ ИЗМЕНЕНИЕ: правильная обработка отправки формы
      submitAndDownloadPdf();

    } catch (Exception e) {
      System.err.println("[ОШИБКА] " + e.getClass().getSimpleName() + ": " + e.getMessage());
      e.printStackTrace();
    } finally {
      closeBrowser();
    }
  }

  private void initializeDriver() {
    System.setProperty("webdriver.chrome.driver", "C:\\ChromeDriver\\chromedriver-win64\\chromedriver.exe");

    ChromeOptions options = new ChromeOptions();
    options.addArguments("--remote-allow-origins=*");
    options.addArguments("--disable-blink-features=AutomationControlled");

    // ОТКЛЮЧАЕМ автоматическое открытие PDF в браузере
    Map<String, Object> prefs = new HashMap<>();
    prefs.put("download.default_directory", Paths.get("result").toAbsolutePath().toString());
    prefs.put("download.prompt_for_download", false);
    prefs.put("download.directory_upgrade", true);
    prefs.put("plugins.always_open_pdf_externally", false); // МЕНЯЕМ на false
    prefs.put("profile.default_content_setting_values.automatic_downloads", 1);

    options.setExperimentalOption("prefs", prefs);

    driver = new ChromeDriver(options);
    wait = new WebDriverWait(driver, TIMEOUT);
    System.out.println("[ИНИЦИАЛИЗАЦИЯ] Драйвер Chrome запущен");
  }

  private void prepareDirectories() throws Exception {
    resultDirectory = Paths.get("result");
    Files.createDirectories(resultDirectory);

    Path dataDirectory = Paths.get("data");
    Files.createDirectories(dataDirectory);
    System.out.println("[ДИРЕКТОРИИ] Папки result и data готовы");
  }

  private FormData loadFormDataFromFile(Path dataFilePath) throws Exception {
    List<String> lines = Files.readAllLines(dataFilePath);

    if (lines.size() < 2) {
      throw new IllegalArgumentException("Файл data.txt должен содержать минимум 2 строки");
    }

    String artist = lines.get(0).trim();
    String title = lines.get(1).trim();

    List<String> tracks = new ArrayList<>();
    for (int i = 2; i < lines.size(); i++) {
      String track = lines.get(i).trim();
      if (!track.isEmpty() && tracks.size() < MAX_TRACKS) {
        tracks.add(track);
      }
    }

    System.out.println("[ЗАГРУЗКА] Данные прочитаны из: " + dataFilePath);
    return new FormData(artist, title, tracks);
  }

  private void printSummary(FormData data) {
    System.out.println("\n========== ДАННЫЕ ДЛЯ ОБЛОЖКИ ==========");
    System.out.println("Исполнитель: " + data.getArtist());
    System.out.println("Альбом: " + data.getTitle());
    System.out.println("Количество треков: " + data.getTracks().size());
    for (int i = 0; i < data.getTracks().size(); i++) {
      System.out.println("  " + (i+1) + ". " + data.getTracks().get(i));
    }
    System.out.println("========================================\n");
  }

  private void navigateToSite() {
    driver.get(BASE_URL);
    System.out.println("[НАВИГАЦИЯ] Открыта страница: " + BASE_URL);

    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("form")));
    System.out.println("[ОЖИДАНИЕ] Страница загружена");
  }

  private void fillArtistAndTitle(String artist, String title) {
    By artistLocator = By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[1]/td[2]/input");
    By titleLocator = By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[2]/td[2]/input");

    WebElement artistField = waitAndGetElement(artistLocator);
    artistField.clear();
    artistField.sendKeys(artist);
    System.out.println("[ЗАПОЛНЕНО] Artist: " + artist);

    WebElement titleField = waitAndGetElement(titleLocator);
    titleField.clear();
    titleField.sendKeys(title);
    System.out.println("[ЗАПОЛНЕНО] Title: " + title);
  }

  private void fillTracks(List<String> tracks) {
    if (tracks.isEmpty()) {
      System.out.println("[ПРЕДУПРЕЖДЕНИЕ] Список треков пуст");
      return;
    }

    for (int i = 0; i < tracks.size() && i < MAX_TRACKS; i++) {
      String trackXpath = buildTrackXpath(i);
      WebElement trackField = waitAndGetElement(By.xpath(trackXpath));
      trackField.clear();
      trackField.sendKeys(tracks.get(i));
      System.out.println("[ЗАПОЛНЕНО] Трек " + (i+1) + ": " + tracks.get(i));
    }
  }

  private String buildTrackXpath(int trackIndex) {
    int column = (trackIndex < TRACKS_PER_COLUMN) ? 1 : 2;
    int row = (trackIndex < TRACKS_PER_COLUMN) ? (trackIndex + 1) : (trackIndex - TRACKS_PER_COLUMN + 1);

    return String.format(
            "/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[3]/td[2]/table/tbody/tr/td[%d]/table/tbody/tr[%d]/td[2]/input",
            column, row
    );
  }

  private void selectJewelCaseOption() {
    By jewelCaseLocator = By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[4]/td[2]/input[2]");
    WebElement jewelCaseRadio = waitAndGetElement(jewelCaseLocator);
    if (!jewelCaseRadio.isSelected()) {
      jewelCaseRadio.click();
    }
    System.out.println("[ВЫБРАНО] Jewel Case");
  }

  private void selectA4PaperOption() {
    By a4Locator = By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/table/tbody/tr[5]/td[2]/input[2]");
    WebElement a4Radio = waitAndGetElement(a4Locator);
    if (!a4Radio.isSelected()) {
      a4Radio.click();
    }
    System.out.println("[ВЫБРАНО] A4");
  }

  // ГЛАВНОЕ ИЗМЕНЕНИЕ: правильная обработка отправки и скачивания
  private void submitAndDownloadPdf() throws Exception {
    // Находим и нажимаем кнопку отправки
    By submitLocator = By.xpath("/html/body/table[2]/tbody/tr/td[1]/div/form/p/input");
    WebElement submitButton = waitAndGetElement(submitLocator);

    String originalWindow = driver.getWindowHandle();
    Set<String> existingWindows = driver.getWindowHandles();

    submitButton.click();
    System.out.println("[ОТПРАВКА] Форма отправлена");

    // Ждем появления новой вкладки или окна
    Thread.sleep(3000);

    // Переключаемся на новую вкладку, если она открылась
    Set<String> allWindows = driver.getWindowHandles();
    if (allWindows.size() > existingWindows.size()) {
      for (String windowHandle : allWindows) {
        if (!existingWindows.contains(windowHandle)) {
          driver.switchTo().window(windowHandle);
          System.out.println("[ПЕРЕКЛЮЧЕНИЕ] Переключились на новую вкладку");
          break;
        }
      }
    }

    // Ждем загрузки PDF (либо в браузере, либо URL с .pdf)
    Thread.sleep(5000);

    String currentUrl = driver.getCurrentUrl();
    System.out.println("[URL] Текущий адрес: " + currentUrl);

    // Пытаемся скачать PDF
    boolean downloaded = false;

    // Способ 1: если URL ведет на PDF
    if (currentUrl.contains(".pdf")) {
      downloaded = downloadFromUrl(currentUrl);
    }

    // Способ 2: ищем ссылку на PDF на странице
    if (!downloaded) {
      downloaded = downloadFromLinkOnPage();
    }

    // Способ 3: используем JavaScript для принудительного скачивания
    if (!downloaded) {
      downloaded = forceDownloadViaJs();
    }

    if (!downloaded) {
      System.out.println("[НЕ УДАЛОСЬ] PDF не найден. Сохраняем страницу для отладки");
      savePageForDebug();
    }

    // Возвращаемся в исходное окно
    driver.close();
    driver.switchTo().window(originalWindow);
  }

  private boolean downloadFromUrl(String pdfUrl) {
    try {
      Path outputPath = resultDirectory.resolve("cd.pdf");
      try (InputStream in = URI.create(pdfUrl).toURL().openStream()) {
        Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
      }

      // Проверяем, что файл действительно PDF (начинается с %PDF)
      byte[] header = Files.readAllBytes(outputPath);
      if (header.length > 4 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F') {
        System.out.println("[УСПЕХ] PDF сохранен и валиден: " + outputPath);
        return true;
      } else {
        System.out.println("[ПРЕДУПРЕЖДЕНИЕ] Файл не похож на PDF, возможно ошибка");
        return false;
      }
    } catch (Exception e) {
      System.out.println("[ОШИБКА] Не удалось скачать из URL: " + e.getMessage());
      return false;
    }
  }

  private boolean downloadFromLinkOnPage() {
    try {
      List<WebElement> links = driver.findElements(By.xpath("//a[contains(@href, '.pdf')]"));
      for (WebElement link : links) {
        String href = link.getAttribute("href");
        if (href != null && href.contains(".pdf")) {
          System.out.println("[НАЙДЕНА] Ссылка на PDF: " + href);
          return downloadFromUrl(href);
        }
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean forceDownloadViaJs() {
    try {
      // JavaScript для принудительного скачивания
      Object result = ((org.openqa.selenium.JavascriptExecutor) driver)
              .executeScript("return window.location.href;");

      String pageUrl = result.toString();
      System.out.println("[JS] Текущий URL через JS: " + pageUrl);

      // Проверяем, не изменился ли URL после JS
      if (pageUrl.contains(".pdf")) {
        return downloadFromUrl(pageUrl);
      }

      // Пытаемся найти embed или object с PDF
      WebElement pdfEmbed = driver.findElement(By.xpath("//embed[contains(@src, '.pdf')] | //iframe[contains(@src, '.pdf')]"));
      if (pdfEmbed != null) {
        String src = pdfEmbed.getAttribute("src");
        if (src != null && src.contains(".pdf")) {
          return downloadFromUrl(src);
        }
      }

      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private void savePageForDebug() {
    try {
      String pageSource = driver.getPageSource();
      Path debugFile = resultDirectory.resolve("debug_page.html");
      Files.writeString(debugFile, pageSource);
      System.out.println("[ДЕБАГ] Сохранена страница: " + debugFile);
    } catch (Exception e) {
      System.out.println("[ДЕБАГ] Не удалось сохранить страницу");
    }
  }

  private WebElement waitAndGetElement(By locator) {
    return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
  }

  private void closeBrowser() {
    if (driver != null) {
      try {
        Thread.sleep(2000);
        driver.quit();
        System.out.println("[ЗАВЕРШЕНИЕ] Браузер закрыт");
      } catch (InterruptedException e) {
        driver.quit();
        Thread.currentThread().interrupt();
      }
    }
  }

  private static class FormData {
    private final String artist;
    private final String title;
    private final List<String> tracks;

    public FormData(String artist, String title, List<String> tracks) {
      this.artist = artist;
      this.title = title;
      this.tracks = tracks;
    }

    public String getArtist() { return artist; }
    public String getTitle() { return title; }
    public List<String> getTracks() { return tracks; }
  }
}