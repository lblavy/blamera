"""Téléchargement volontaire de la galerie, sans fichier personnel automatique."""

import streamlit as st

from .evaluation_store import EvaluationStore
from .face_gallery_export import build_export
from .gallery import GalleryError
from .gallery_store import GalleryStore


def render_export(gallery: GalleryStore, evaluation: EvaluationStore) -> None:
    try:
        payload = build_export(gallery, evaluation)
    except GalleryError as error:
        st.error(str(error))
        return
    st.download_button(
        label="Exporter la galerie",
        data=payload,
        file_name="blamera-face-gallery-v1.json",
        mime="application/json",
        on_click="ignore",
        type="primary",
    )
