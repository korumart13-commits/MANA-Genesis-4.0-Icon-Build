# MANA local model attribution

MANA's initial on-device text model is:

- Model: Qwen3-0.6B INT4 LiteRT-LM conversion
- Publisher: LiteRT Community
- Model repository: https://huggingface.co/litert-community/Qwen3-0.6B-int4
- Base model: Qwen/Qwen3-0.6B
- License: Apache License 2.0, as stated by the model repository and inherited from the base model.
- Selected file: qwen3_0.6b_q4_block32_ekv1280.litertlm
- Pinned revision: 6aa2daf
- SHA-256: 312ce4e6bc0816edf844b9b7e66542648a89440b54822c3581e48a19e9ff5715

The model is downloaded by the app from the pinned public repository into app-private storage. MANA does not redistribute a copy inside its APK. The app verifies the downloaded bytes against the SHA-256 above before enabling local inference.

Apache License 2.0 text: https://www.apache.org/licenses/LICENSE-2.0
