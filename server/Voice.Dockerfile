# GPU runtime. NVIDIA Container Toolkit is required on the host.
FROM pytorch/pytorch:2.7.1-cuda12.8-cudnn9-runtime
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 HF_HOME=/models/huggingface
WORKDIR /srv/mana
COPY voice-requirements.txt .
RUN pip install --no-cache-dir -r voice-requirements.txt
COPY voice_app.py .
RUN useradd --system --uid 10002 mana && mkdir -p /models/huggingface && chown -R mana:mana /srv/mana /models
USER mana
EXPOSE 8081
CMD ["uvicorn", "voice_app:app", "--host", "0.0.0.0", "--port", "8081", "--proxy-headers"]
