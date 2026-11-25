import os
import unittest
from fastapi.testclient import TestClient

from app.main import app


class GatewayContractTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        self.old = {
            key: os.environ.get(key)
            for key in ("MANA_DEVICE_BEARER_TOKEN", "MANA_MODEL_BASE_URL", "MANA_MODEL_NAME", "MANA_MODEL_API_KEY")
        }

    def tearDown(self):
        for key, value in self.old.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value

    def test_health_does_not_expose_secrets(self):
        os.environ["MANA_DEVICE_BEARER_TOKEN"] = "x" * 40
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertNotIn("x" * 40, response.text)
        self.assertFalse(response.json()["model_configured"])

    def test_respond_requires_authentication(self):
        os.environ["MANA_DEVICE_BEARER_TOKEN"] = "d" * 40
        response = self.client.post("/v1/mana/respond", json={"protocol_version": 1})
        self.assertEqual(response.status_code, 401)

    def test_unconfigured_model_fails_closed(self):
        os.environ["MANA_DEVICE_BEARER_TOKEN"] = "d" * 40
        for key in ("MANA_MODEL_BASE_URL", "MANA_MODEL_NAME", "MANA_MODEL_API_KEY"):
            os.environ.pop(key, None)
        payload = {
            "protocol_version": 1,
            "conversation_id": "test",
            "cycle": 0,
            "input": "Привет",
            "memories": [],
            "dialogue": [],
            "sources": [],
            "observations": [],
            "available_tools": [],
            "context_trust": "untrusted",
        }
        response = self.client.post(
            "/v1/mana/respond",
            headers={"Authorization": "Bearer " + "d" * 40},
            json=payload,
        )
        self.assertEqual(response.status_code, 503)


if __name__ == "__main__":
    unittest.main()
