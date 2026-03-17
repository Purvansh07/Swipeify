from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import Optional, List
from database import get_db
from models import User, LikedSong, Playlist, PlaylistSong
from config import SECRET_KEY, ALGORITHM, ACCESS_TOKEN_EXPIRE_MINUTES
import bcrypt
from jose import jwt, JWTError
from datetime import datetime, timedelta

router = APIRouter()
security = HTTPBearer()

# ─── Request Models ───────────────────────────────────────────────────────────

class RegisterRequest(BaseModel):
    full_name: str
    username: str
    email: str
    password: str
    phone: str = ""

class LoginRequest(BaseModel):
    email: str
    password: str

class LikeSongRequest(BaseModel):
    spotify_track_id: str
    track_name: str
    artist_name: str
    album_name: str
    album_art_url: Optional[str] = None
    preview_url: Optional[str] = None

class CreatePlaylistRequest(BaseModel):
    name: str

class AddSongToPlaylistRequest(BaseModel):
    spotify_track_id: str
    track_name: str
    artist_name: str
    album_name: str
    album_art_url: Optional[str] = None
    preview_url: Optional[str] = None

# ─── Helper Functions ─────────────────────────────────────────────────────────

def create_token(user_id: int):
    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    return jwt.encode(
        {"sub": str(user_id), "exp": expire},
        SECRET_KEY,
        algorithm=ALGORITHM
    )

def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: Session = Depends(get_db)
):
    try:
        token = credentials.credentials
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        user_id = int(payload.get("sub"))
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=401, detail="User not found")
        return user
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid token")

# ─── Auth Routes ──────────────────────────────────────────────────────────────

@router.post("/register")
def register(data: RegisterRequest, db: Session = Depends(get_db)):
    if db.query(User).filter(User.email == data.email).first():
        raise HTTPException(status_code=409, detail="Email already registered")
    if db.query(User).filter(User.username == data.username).first():
        raise HTTPException(status_code=409, detail="Username already taken")
    hashed = bcrypt.hashpw(data.password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")
    user = User(full_name=data.full_name, username=data.username, email=data.email, password=hashed, phone=data.phone)
    db.add(user)
    db.commit()
    db.refresh(user)
    token = create_token(user.id)
    return {"message": "Account created successfully", "token": token, "user": {"id": user.id, "full_name": user.full_name, "username": user.username, "email": user.email}}

@router.post("/login")
def login(data: LoginRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == data.email).first()
    if not user or not bcrypt.checkpw(data.password.encode("utf-8"), user.password.encode("utf-8")):
        raise HTTPException(status_code=401, detail="Invalid email or password")
    token = create_token(user.id)
    return {"message": "Logged in successfully", "token": token, "user": {"id": user.id, "full_name": user.full_name, "username": user.username, "email": user.email}}

# ─── Liked Songs Routes ───────────────────────────────────────────────────────

@router.post("/songs/like")
def like_song(data: LikeSongRequest, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    existing = db.query(LikedSong).filter(LikedSong.user_id == current_user.id, LikedSong.spotify_track_id == data.spotify_track_id).first()
    if existing:
        return {"message": "Song already liked"}
    song = LikedSong(user_id=current_user.id, spotify_track_id=data.spotify_track_id, track_name=data.track_name, artist_name=data.artist_name, album_name=data.album_name, album_art_url=data.album_art_url, preview_url=data.preview_url)
    db.add(song)
    db.commit()
    return {"message": "Song liked successfully"}

@router.get("/songs/liked")
def get_liked_songs(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    songs = db.query(LikedSong).filter(LikedSong.user_id == current_user.id).order_by(LikedSong.liked_at.desc()).all()
    return {"songs": [{"id": s.id, "spotify_track_id": s.spotify_track_id, "track_name": s.track_name, "artist_name": s.artist_name, "album_name": s.album_name, "album_art_url": s.album_art_url, "preview_url": s.preview_url, "liked_at": str(s.liked_at)} for s in songs]}

@router.delete("/songs/unlike/{track_id}")
def unlike_song(track_id: str, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    song = db.query(LikedSong).filter(LikedSong.user_id == current_user.id, LikedSong.spotify_track_id == track_id).first()
    if not song:
        raise HTTPException(status_code=404, detail="Song not found")
    db.delete(song)
    db.commit()
    return {"message": "Song removed"}

# ─── Playlist Routes ──────────────────────────────────────────────────────────

@router.post("/playlists/create")
def create_playlist(data: CreatePlaylistRequest, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    playlist = Playlist(user_id=current_user.id, name=data.name)
    db.add(playlist)
    db.commit()
    db.refresh(playlist)
    return {"message": "Playlist created", "playlist": {"id": playlist.id, "name": playlist.name, "created_at": str(playlist.created_at)}}

@router.get("/playlists")
def get_playlists(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    playlists = db.query(Playlist).filter(Playlist.user_id == current_user.id).order_by(Playlist.created_at.desc()).all()
    result = []
    for p in playlists:
        songs = db.query(PlaylistSong).filter(PlaylistSong.playlist_id == p.id).all()
        result.append({
            "id": p.id,
            "name": p.name,
            "song_count": len(songs),
            "created_at": str(p.created_at),
            "cover_art": songs[0].album_art_url if songs else None
        })
    return {"playlists": result}

@router.get("/playlists/{playlist_id}/songs")
def get_playlist_songs(playlist_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    playlist = db.query(Playlist).filter(Playlist.id == playlist_id, Playlist.user_id == current_user.id).first()
    if not playlist:
        raise HTTPException(status_code=404, detail="Playlist not found")
    songs = db.query(PlaylistSong).filter(PlaylistSong.playlist_id == playlist_id).all()
    return {
        "playlist": {"id": playlist.id, "name": playlist.name},
        "songs": [{"id": s.id, "spotify_track_id": s.spotify_track_id, "track_name": s.track_name, "artist_name": s.artist_name, "album_name": s.album_name, "album_art_url": s.album_art_url, "preview_url": s.preview_url} for s in songs]
    }

@router.post("/playlists/{playlist_id}/songs")
def add_song_to_playlist(playlist_id: int, data: AddSongToPlaylistRequest, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    playlist = db.query(Playlist).filter(Playlist.id == playlist_id, Playlist.user_id == current_user.id).first()
    if not playlist:
        raise HTTPException(status_code=404, detail="Playlist not found")
    existing = db.query(PlaylistSong).filter(PlaylistSong.playlist_id == playlist_id, PlaylistSong.spotify_track_id == data.spotify_track_id).first()
    if existing:
        return {"message": "Song already in playlist"}
    song = PlaylistSong(playlist_id=playlist_id, user_id=current_user.id, spotify_track_id=data.spotify_track_id, track_name=data.track_name, artist_name=data.artist_name, album_name=data.album_name, album_art_url=data.album_art_url, preview_url=data.preview_url)
    db.add(song)
    db.commit()
    return {"message": "Song added to playlist"}

@router.delete("/playlists/{playlist_id}")
def delete_playlist(playlist_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    playlist = db.query(Playlist).filter(Playlist.id == playlist_id, Playlist.user_id == current_user.id).first()
    if not playlist:
        raise HTTPException(status_code=404, detail="Playlist not found")
    db.query(PlaylistSong).filter(PlaylistSong.playlist_id == playlist_id).delete()
    db.delete(playlist)
    db.commit()
    return {"message": "Playlist deleted"}