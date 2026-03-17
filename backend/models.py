from sqlalchemy import Column, Integer, String, DateTime, ForeignKey
from sqlalchemy.sql import func
from database import Base

class User(Base):
    __tablename__ = "users"
    __table_args__ = {"extend_existing": True}
    id = Column(Integer, primary_key=True, index=True)
    full_name = Column(String, nullable=False)
    username = Column(String, unique=True, nullable=False, index=True)
    email = Column(String, unique=True, nullable=False, index=True)
    password = Column(String, nullable=False)
    phone = Column(String, nullable=True)
    spotify_token = Column(String, nullable=True)
    created_at = Column(DateTime, server_default=func.now())

class Playlist(Base):
    __tablename__ = "playlists"
    __table_args__ = {"extend_existing": True}
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    name = Column(String, nullable=False)
    created_at = Column(DateTime, server_default=func.now())

class PlaylistSong(Base):
    __tablename__ = "playlist_songs"
    __table_args__ = {"extend_existing": True}
    id = Column(Integer, primary_key=True, index=True)
    playlist_id = Column(Integer, ForeignKey("playlists.id"), nullable=False)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    spotify_track_id = Column(String, nullable=False)
    track_name = Column(String, nullable=False)
    artist_name = Column(String, nullable=False)
    album_name = Column(String, nullable=False)
    album_art_url = Column(String, nullable=True)
    preview_url = Column(String, nullable=True)
    added_at = Column(DateTime, server_default=func.now())

class LikedSong(Base):
    __tablename__ = "liked_songs"
    __table_args__ = {"extend_existing": True}
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    spotify_track_id = Column(String, nullable=False)
    track_name = Column(String, nullable=False)
    artist_name = Column(String, nullable=False)
    album_name = Column(String, nullable=False)
    album_art_url = Column(String, nullable=True)
    preview_url = Column(String, nullable=True)
    liked_at = Column(DateTime, server_default=func.now())