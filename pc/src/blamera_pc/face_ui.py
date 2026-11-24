"""Choix manuel du visage, sans calcul implicite ni décision d'identité."""

from io import BytesIO

import streamlit as st
from PIL import Image

from .face_data import FaceEngine, FaceState
from .face_engine import OpenCvFaceEngine
from .face_models import load_configuration
from .face_service import confirm_face, prepare_faces
from .face_store import FaceReferences
from .gallery import GalleryError, Photo
from .gallery_store import GalleryStore


@st.cache_resource(show_spinner=False)
def _engine(profile_key: str) -> FaceEngine:
    configuration = load_configuration()
    if configuration.profile.key != profile_key:
        raise GalleryError("Le profil a changé. Relancez la préparation.")
    return OpenCvFaceEngine(configuration)


def render_faces(store: GalleryStore, photo: Photo) -> None:
    try:
        configuration = load_configuration()
        references = FaceReferences(store)
        if st.button("Préparer les visages", key=f"prepare_faces_{photo.id}"):
            with st.spinner("Détection des visages…"):
                prepare_faces(references, photo, _engine(configuration.profile.key))
            st.rerun()
        state = references.read(photo, configuration.profile)
        if state is not None:
            _candidates(photo, state)
            _confirmation(photo, state, references, configuration.profile.key)
    except GalleryError as error:
        st.error(str(error))


def _candidates(photo: Photo, state: FaceState) -> None:
    if not state.candidates:
        st.caption("Aucun visage détecté.")
        return
    previews = []
    with Image.open(BytesIO(photo.image)) as image:
        for candidate in state.candidates:
            x, y, width, height = candidate.values[:4]
            with image.crop(
                (int(x), int(y), int(x + width + 0.999), int(y + height + 0.999))
            ) as crop:
                crop.thumbnail((112, 112))
                output = BytesIO()
                crop.save(output, "PNG")
                previews.append(output.getvalue())
    st.caption(f"{len(previews)} visage(s) détecté(s)")
    st.image(previews, caption=[f"Visage {index + 1}" for index in range(len(previews))], width=112)


def _confirmation(
    photo: Photo, state: FaceState, references: FaceReferences, profile_key: str
) -> None:
    if not state.candidates:
        return
    configuration = load_configuration()
    if configuration.profile.key != profile_key:
        raise GalleryError("Le profil a changé ; préparez à nouveau la photo.")
    if state.reference is not None:
        st.success("Visage confirmé")
        st.image(
            state.reference.sample.crop,
            caption=f"Référence : visage {state.reference.candidate + 1}",
            width=112,
        )
    selected = st.radio(
        "Visage à associer",
        range(len(state.candidates)),
        index=state.selected,
        format_func=lambda value: f"Visage {value + 1}",
        key=f"face_choice_{photo.id}_{state.generation}",
    )
    if selected is not None and selected != state.selected:
        references.select(photo, configuration.profile, selected, generation=state.generation)
    if st.button(
        "Enregistrer ce visage", key=f"confirm_face_{photo.id}", disabled=selected is None
    ):
        if selected is not None:
            with st.spinner("Préparation de la référence…"):
                confirm_face(references, photo, selected, _engine(profile_key))
            st.rerun()
