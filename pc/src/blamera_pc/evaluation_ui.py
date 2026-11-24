"""Parcours d'évaluation explicite, distinct de l'enregistrement des références."""

from pathlib import Path

import streamlit as st

from .evaluation_absence_ui import render_absence
from .evaluation_data import Policy
from .evaluation_final_ui import render_final_test
from .evaluation_report_ui import render_report
from .evaluation_selection_ui import render_reference_selection
from .evaluation_service import current_report, run_evaluation
from .evaluation_sessions_ui import render_sessions
from .evaluation_snapshot import Snapshot, snapshot
from .evaluation_store import Case, EvaluationStore
from .face_models import load_configuration
from .face_ui import render_faces
from .gallery import GalleryError
from .gallery_store import GalleryStore
from .image_import import MAX_BATCH, normalize_image

UNKNOWN = "__unknown__"
SPLITS = {"calibration": "Calibration", "test": "Test final réservé"}


def render_evaluation(directory: Path) -> None:
    st.subheader("Évaluation des visages")
    st.caption(
        "Calibration : essais et réglages. Test final réservé : photos gardées pour l'évaluation finale."
    )
    try:
        main, store = GalleryStore(directory), EvaluationStore(directory)
        profile = load_configuration().profile
        view = snapshot(main, store, profile)
        render_reference_selection(view, store)
        render_sessions(view, store)
        _import_form(view, store)
        _policy_form(store)
        if st.button("Évaluer la calibration", type="primary"):
            try:
                with st.spinner("Comparaison des visages…"):
                    run_evaluation(main, store, profile)
                st.rerun()
            except GalleryError as error:
                st.error(str(error))
        report = current_report(main, store, profile)
        if report is not None:
            render_report(report)
        else:
            st.caption(
                "Lancez ou relancez l'évaluation après les préparations et les modifications."
            )
        frozen = render_final_test(main, store, profile)
        _corpus(view, store, frozen)
    except GalleryError as error:
        st.error(str(error))


def _identities(view: Snapshot) -> dict[str, str]:
    return {
        **dict(sorted(view.names.items(), key=lambda item: item[1].casefold())),
        UNKNOWN: "Personne inconnue",
    }


def _import_form(view: Snapshot, store: EvaluationStore) -> None:
    names = _identities(view)
    with st.expander("Ajouter des photos d'évaluation", expanded=not view.probes):
        generation = st.session_state.get("eval_upload_generation", 0)
        with st.form("import_evaluation", clear_on_submit=False):
            columns = st.columns(2)
            expected = columns[0].selectbox(
                "Identité attendue",
                list(names),
                format_func=lambda key: names[key],
                key="eval_expected",
            )
            split = columns[1].selectbox(
                "Lot", list(SPLITS), format_func=lambda key: SPLITS[key], key="eval_split"
            )
            session = st.text_input("Séance des nouvelles photos", max_chars=80, key="eval_session")
            files = st.file_uploader(
                "Photos d'évaluation",
                type=["jpg", "jpeg", "png", "webp"],
                accept_multiple_files=True,
                max_upload_size=20,
                key=f"eval_uploads_{generation}",
            )
            submitted = st.form_submit_button("Importer dans le corpus", disabled=not files)
        if submitted and files:
            if len(files) > MAX_BATCH:
                raise GalleryError("Maximum 20 photos par import ; aucune photo ajoutée.")
            outcomes = []
            for file in files:
                try:
                    store.add_case(
                        normalize_image(file.getvalue()),
                        None if expected == UNKNOWN else expected,
                        split,
                        session,
                        view.digests,
                        view.reference_sessions,
                    )
                    message = "ajoutée"
                except GalleryError as error:
                    message = str(error)
                outcomes.append(f"{file.name} — {message}")
            st.session_state["eval_import_results"] = outcomes
            st.session_state["eval_upload_generation"] = generation + 1
            st.rerun()
        for outcome in st.session_state.get("eval_import_results", []):
            st.write(outcome)


def _policy_form(store: EvaluationStore) -> None:
    policy = store.policy()
    with st.expander("Paramètres de comparaison"):
        st.caption(
            "Seuils exploratoires, non calibrés. Les scores sont des similarités, pas des probabilités."
        )
        with st.form("evaluation_policy"):
            columns = st.columns(3)
            reject = columns[0].number_input(
                "Seuil de rejet", min_value=-1.0, max_value=1.0, value=policy.reject, step=0.05
            )
            accept = columns[1].number_input(
                "Seuil d'acceptation", min_value=-1.0, max_value=1.0, value=policy.accept, step=0.05
            )
            margin = columns[2].number_input(
                "Marge entre identités",
                min_value=0.0,
                max_value=2.0,
                value=policy.margin,
                step=0.01,
            )
            if st.form_submit_button("Appliquer les paramètres"):
                store.set_policy(Policy(reject, accept, margin))
                st.rerun()


def _corpus(view: Snapshot, store: EvaluationStore, frozen: bool) -> None:
    st.subheader("Photos du corpus")
    selected = st.radio(
        "Afficher le lot", list(SPLITS), format_func=lambda key: SPLITS[key], horizontal=True
    )
    probes = [probe for probe in view.probes if probe.case.split == selected]
    st.caption(f"{len(probes)} photo(s) · {SPLITS[selected]}")
    if selected == "test" and not frozen:
        st.caption("Figez les paramètres pour préparer les visages du Test final.")
    columns = st.columns(3)
    for index, probe in enumerate(probes):
        with columns[index % 3]:
            _case_card(view, store, probe.case, index + 1, selected != "test" or frozen)


def _case_card(
    view: Snapshot, store: EvaluationStore, case: Case, index: int, prepare: bool
) -> None:
    st.image(case.photo.image, width="stretch")
    st.caption(
        f"Image {index} · {view.names.get(case.expected, 'Identité retirée') if case.expected else 'Personne inconnue'} · {case.session}"
    )
    if prepare and not render_absence(store, case, view.profile):
        render_faces(store, case.photo)
    names = _identities(view)
    expected = case.expected or UNKNOWN
    if expected not in names:
        names[expected] = "Identité retirée — à corriger"
    with st.expander("Modifier l'annotation"):
        with st.form(f"edit_case_{case.photo.id}"):
            identity = st.selectbox(
                "Identité attendue",
                list(names),
                index=list(names).index(expected),
                format_func=lambda key: names[key],
                key=f"eval_identity_{case.photo.id}",
            )
            split = st.selectbox(
                "Lot",
                list(SPLITS),
                index=list(SPLITS).index(case.split),
                format_func=lambda key: SPLITS[key],
                key=f"eval_split_{case.photo.id}",
            )
            session = st.text_input(
                "Séance", value=case.session, max_chars=80, key=f"eval_session_{case.photo.id}"
            )
            if st.form_submit_button("Enregistrer l'annotation"):
                store.update_case(
                    case.photo.id,
                    None if identity == UNKNOWN else identity,
                    split,
                    session,
                    view.reference_sessions,
                )
                st.rerun()
        confirmed = st.checkbox(
            "Confirmer le retrait du corpus", key=f"eval_delete_{case.photo.id}"
        )
        if st.button(
            "Retirer du corpus", disabled=not confirmed, key=f"eval_remove_{case.photo.id}"
        ):
            store.delete_photo(case.photo.id)
            st.rerun()
