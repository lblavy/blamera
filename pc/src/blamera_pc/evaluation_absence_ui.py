"""Action volontaire d'absence du sujet dans les seules cartes d'évaluation."""

import streamlit as st

from .evaluation_absence import TargetAbsences
from .evaluation_store import Case, EvaluationStore
from .face_data import FaceProfile
from .face_store import FaceReferences


def render_absence(store: EvaluationStore, case: Case, profile: FaceProfile) -> bool:
    state = FaceReferences(store).read(case.photo, profile)
    if state is None or not state.candidates:
        return False
    with store._connection() as connection:
        missing = TargetAbsences.read(connection, case, profile, state)
    absence = TargetAbsences(store)
    if missing:
        st.caption("Sujet attendu non détecté · cas non exploitable")
        if st.button("Annuler le constat d'absence", key=f"restore_target_{case.photo.id}"):
            absence.clear(case, profile, generation=state.generation)
            st.rerun()
    elif st.button("Sujet attendu non détecté", key=f"missing_target_{case.photo.id}"):
        absence.mark(case, profile, generation=state.generation)
        st.rerun()
    return missing
