from fastapi import FastAPI
from database import engine, Base
import models
from auth import router

Base.metadata.create_all(bind=engine)

app = FastAPI(title="Swipeify API")
app.include_router(router, prefix="/api/auth")

@app.get("/")
def root():
    return {"message": "Swipeify API is running!"}