"""Affectation en lot des séances des seules références confirmées."""

import streamlit as st

from .evaluation_snapshot import Snapshot
from .evaluation_store import EvaluationStore


def render_sessions(view: Snapshot, store: EvaluationStore) -> None:
    with st.expander(
        "Séances des références", expanded=any(not ref.session for ref in view.references)
    ):
        st.caption(
            "Même moment ou même rafale : même séance. Utilisez d'autres séances pour les nouvelles photos."
        )
        if not view.references:
            st.info("Confirmez d'abord des visages dans Références.")
            return
        owners = sorted(
            {ref.photo.person_id for ref in view.references},
            key=lambda key: view.names[key].casefold(),
        )
        owner = st.selectbox(
            "Personne des références",
            owners,
            format_func=lambda key: view.names[key],
            key="ref_session_owner",
        )
        refs = [ref for ref in view.references if ref.photo.person_id == owner]
        by_id = {ref.photo.id: ref for ref in refs}
        labels = {
            ref.photo.id: f"Photo {index + 1} — {ref.session or 'séance à renseigner'}"
            for index, ref in enumerate(refs)
        }
        with st.form(f"reference_sessions_{owner}"):
            selected = st.multiselect(
                "Photos de référence",
                list(by_id),
                default=list(by_id),
                format_func=lambda key: labels[key],
                key=f"ref_session_photos_{owner}",
            )
            if selected:
                st.image([by_id[key].photo.image for key in selected], width=100)
            session = st.text_input("Séance des références", max_chars=80, key="ref_session_name")
            if st.form_submit_button("Associer la séance"):
                store.assign_sessions([by_id[key].photo for key in selected], session)
                st.rerun()
