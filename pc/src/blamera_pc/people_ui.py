"""Présentation de la galerie ; chaque mutation vient d'une action explicite."""

import os
from pathlib import Path

import streamlit as st

from .face_ui import render_faces
from .gallery import GalleryError, Person, Photo
from .gallery_store import GalleryStore
from .image_import import MAX_BATCH
from .photo_batch import PhotoUpload, import_batch


def data_directory() -> Path:
    configured = os.environ.get("BLAMERA_DATA_DIR")
    return Path(configured) if configured else Path(__file__).resolve().parents[2] / "data"


def render_people() -> None:
    st.subheader("Galerie des proches")
    st.caption("Galerie · visages : ArcFace ResNet-50")
    try:
        store = GalleryStore(data_directory())
        _creation(store)
        people = store.people()
        if not people:
            st.info("Ajoutez une personne, puis ses photos de référence.")
            return
        _selection(store, people)
    except GalleryError as error:
        st.error(str(error))


def _creation(store: GalleryStore) -> None:
    with st.form("create_person", clear_on_submit=True):
        name = st.text_input("Nouvelle personne", key="new_person_name", max_chars=80)
        if st.form_submit_button("Ajouter"):
            st.session_state["person_id"] = store.create(name)
            st.rerun()


def _selection(store: GalleryStore, people: list[Person]) -> None:
    labels = {person.id: f"{person.name} · {person.count} photo(s)" for person in people}
    if st.session_state.get("person_id") not in labels:
        st.session_state["person_id"] = people[0].id
    identifier = st.selectbox(
        "Personne", list(labels), format_func=labels.__getitem__, key="person_id"
    )
    person = next(person for person in people if person.id == identifier)
    _person_settings(store, person)
    _upload(store, person.id)
    photos = store.photos(person.id)
    st.caption(f"{len(photos)} photo(s) de référence")
    columns = st.columns(3)
    for index, photo in enumerate(photos):
        with columns[index % 3]:
            _photo_card(store, photo, people)


def _person_settings(store: GalleryStore, person: Person) -> None:
    with st.expander("Modifier la personne"):
        with st.form(f"rename_person_{person.id}"):
            name = st.text_input("Nom", value=person.name, key=f"rename_{person.id}", max_chars=80)
            if st.form_submit_button("Renommer"):
                store.rename(person.id, name)
                st.rerun()
        confirmed = st.checkbox(
            "Confirmer la suppression de cette personne et de ses photos",
            key=f"delete_person_{person.id}",
        )
        if st.button("Supprimer la personne", disabled=not confirmed):
            store.delete_person(person.id)
            st.rerun()


def _upload(store: GalleryStore, identifier: str) -> None:
    generation_key = f"upload_generation_{identifier}"
    generation = st.session_state.get(generation_key, 0)
    files = st.file_uploader(
        "Ajouter des photos",
        type=["jpg", "jpeg", "png", "webp"],
        accept_multiple_files=True,
        max_upload_size=20,
        key=f"uploads_{identifier}_{generation}",
    )
    if st.button("Importer les photos", disabled=not files):
        if len(files) > MAX_BATCH:
            st.error("Maximum 20 photos par import ; aucune photo ajoutée.")
            return
        uploads = [PhotoUpload(file.name, file.getvalue()) for file in files]
        st.session_state[f"import_results_{identifier}"] = import_batch(store, identifier, uploads)
        st.session_state[generation_key] = generation + 1
        st.rerun()
    for outcome in st.session_state.get(f"import_results_{identifier}", []):
        st.write(f"{outcome.name} — {outcome.message}")


def _photo_card(store: GalleryStore, photo: Photo, people: list[Person]) -> None:
    st.image(photo.image, width="stretch")
    st.caption(f"{photo.width} × {photo.height}")
    render_faces(store, photo)
    destinations = {person.id: person.name for person in people if person.id != photo.person_id}
    if destinations:
        destination = st.selectbox(
            "Affecter à",
            list(destinations),
            format_func=destinations.__getitem__,
            key=f"move_{photo.id}",
        )
        if st.button("Déplacer", key=f"move_button_{photo.id}"):
            store.move_photo(photo.id, destination)
            st.rerun()
    confirmed = st.checkbox("Confirmer le retrait", key=f"delete_photo_{photo.id}")
    if st.button("Retirer la photo", key=f"delete_button_{photo.id}", disabled=not confirmed):
        store.delete_photo(photo.id)
        st.rerun()
