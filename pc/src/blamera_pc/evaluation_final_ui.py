"""Ouverture explicite du test final et restitution séparée de la calibration."""

from typing import cast

import streamlit as st

from .evaluation_final import (
    current_final_report,
    current_freeze,
    freeze_final_test,
    run_final_test,
)
from .evaluation_report_ui import render_report
from .evaluation_store import EvaluationStore
from .face_data import FaceProfile
from .face_gallery_export_ui import render_export
from .gallery import GalleryError
from .gallery_store import GalleryStore


def render_final_test(gallery: GalleryStore, store: EvaluationStore, profile: FaceProfile) -> bool:
    st.subheader("Test final")
    frozen = current_freeze(gallery, store, profile)
    if frozen is None:
        st.caption(
            "Figez les paramètres après la calibration, puis confirmez les visages du lot réservé."
        )
    else:
        policy = cast(dict[str, float], frozen["policy"])
        st.caption(
            f"Paramètres figés · rejet {policy['reject']:.2f} · "
            f"acceptation {policy['accept']:.2f} · marge {policy['margin']:.2f}"
        )
    if st.button("Figer les paramètres pour le test final"):
        try:
            freeze_final_test(gallery, store, profile)
            st.rerun()
        except GalleryError as error:
            st.error(str(error))
    if st.button("Évaluer le test final", type="primary", disabled=frozen is None):
        try:
            with st.spinner("Évaluation du lot final…"):
                run_final_test(gallery, store, profile)
            st.rerun()
        except GalleryError as error:
            st.error(str(error))
    report = current_final_report(gallery, store, profile)
    if report is not None:
        render_report(report)
        render_export(gallery, store)
    else:
        with store._connection() as connection:
            history = connection.execute("SELECT 1 FROM final_runs LIMIT 1").fetchone()
        if history:
            st.caption(
                "Un résultat historique est conservé ; il ne correspond plus à l'état actuel. "
                "Les photos déjà évaluées ne peuvent pas servir à un nouveau réglage final."
            )
    return frozen is not None
