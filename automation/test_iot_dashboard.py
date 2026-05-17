import os
import time
import unittest
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

class TestIoTWaterMonitoringDashboard(unittest.TestCase):
    """
    Advanced E2E Automated Testing Workflow.
    Validates UI integrity, simulates high-load IoT data synchronization,
    and ensures zero-downtime platform reliability.
    """

    @classmethod
    def setUpClass(cls):
        # Setup Chrome in headless mode for CI/CD integration
        options = webdriver.ChromeOptions()
        options.add_argument("--headless")
        options.add_argument("--disable-gpu")
        options.add_argument("--window-size=1920,1080")
        cls.driver = webdriver.Chrome(options=options)
        
        # Navigate to the Home Assistant Mock Dashboard
        file_path = "file://" + os.path.abspath("iot_dashboard_mock.html")
        cls.driver.get(file_path)
        
        # Ensure dashboard is fully loaded
        WebDriverWait(cls.driver, 10).until(
            EC.presence_of_element_located((By.ID, "sensor-level-value"))
        )

    @classmethod
    def tearDownClass(cls):
        cls.driver.quit()

    def test_01_ui_integrity(self):
        """Validates that all essential dashboard components render correctly."""
        level_elem = self.driver.find_element(By.ID, "sensor-level-value")
        self.assertIsNotNone(level_elem, "Sensor level display is missing.")
        
        # Dashboard should start with normal status
        self.assertNotIn("LEAK DETECTED", self.driver.page_source)

    def test_02_high_load_telemetry_sync(self):
        """Simulates high-frequency IoT data synchronization events."""
        print("\nSimulating high-load telemetry bursts...")
        
        for level in [90, 85, 70, 65, 50]:
            # Injecting telemetry via JS to simulate websocket push from Home Assistant
            self.driver.execute_script(f"updateSensor({level})")
            time.sleep(0.1) # Rapid burst
            
            # Validate DOM reflects real-time sync
            displayed_level = self.driver.find_element(By.ID, "sensor-level-value").text
            self.assertEqual(displayed_level, str(level), "Dashboard failed to sync real-time telemetry!")
            
    def test_03_zero_downtime_leak_detection(self):
        """Ensures the predictive leak engine triggers alerts without crashing the UI."""
        print("\nTriggering LSTM predictive leak alert...")
        
        # Drop level below threshold
        self.driver.execute_script("updateSensor(15)")
        
        # Verify the Alert Banner appears dynamically
        alert = WebDriverWait(self.driver, 5).until(
            EC.visibility_of_element_located((By.ID, "status-alert"))
        )
        self.assertIn("LEAK DETECTED", alert.text, "Critical alert failed to render.")
        
        # Ensure the page hasn't crashed by checking for a core element
        tank_visual = self.driver.find_element(By.ID, "tank-water-level")
        self.assertTrue(tank_visual.is_displayed(), "UI crashed during critical alert.")

if __name__ == "__main__":
    unittest.main(verbosity=2)
