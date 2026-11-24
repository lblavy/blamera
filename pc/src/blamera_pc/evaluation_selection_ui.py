"""Choisir les références de comparaison sans modifier l'enrôlement."""

import streamlit as st

from .evaluation_snapshot import Snapshot
from .evaluation_store import EvaluationStore


def render_reference_selection(view: Snapshot, store: EvaluationStore) -> None:
    available = view.available_references
    st.caption(f"{len(view.references)} références retenues sur {len(available)} disponibles")
    with st.expander("Références utilisées pour l'évaluation"):
        st.caption(
            "Cette sélection s'applique uniquement à l'évaluation. La galerie est conservée."
        )
        labels = {
            ref.photo.id: f"{view.names[ref.photo.person_id]} — référence {index + 1}"
            for index, ref in enumerate(available)
        }
        for index, identifier in enumerate(view.unavailable_reference_ids):
            labels[identifier] = f"Référence indisponible {index + 1} — à retirer"
        if view.unavailable_reference_ids:
            st.warning("Une référence retenue est indisponible ; corrigez la sélection.")
        generation = st.session_state.get("reference_selection_generation", 0)
        selected = (
            list(labels) if view.reference_selection is None else list(view.reference_selection)
        )
        with st.form("evaluation_reference_selection_form"):
            choices = st.multiselect(
                "Références retenues",
                list(labels),
                default=selected,
                format_func=lambda key: labels[key],
                key=f"evaluation_reference_selection_{generation}",
            )
            previews = [ref.photo.image for ref in available if ref.photo.id in choices]
            if previews:
                st.image(previews, width=100)
            if st.form_submit_button("Appliquer la sélection"):
                store.set_reference_selection(choices)
                _rerun(generation)
        if st.button("Utiliser toutes les références"):
            store.set_reference_selection(None)
            _rerun(generation)


def _rerun(generation: int) -> None:
    st.session_state["reference_selection_generation"] = generation + 1
    st.rerun()
