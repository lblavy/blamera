"""Restitution de compteurs avec dénominateurs et décisions par image."""

from typing import cast

import streamlit as st

COUNT_LABELS = {
    "total": "Images",
    "usable": "Exploitables",
    "unusable": "Non exploitables",
    "correct_names": "Bons noms",
    "false_names": "Faux noms",
    "false_names_known": "Faux noms sur proches",
    "false_names_unknown": "Faux noms sur inconnus",
    "correct_unknown": "Inconnus rejetés",
    "false_rejections": "Proches rejetés",
    "uncertain": "Incertains",
}


def render_report(report: dict[str, object]) -> None:
    counts = cast(dict[str, int], report["counts"])
    final = report.get("split") == "test"
    st.subheader("Bilan du Test final" if final else "Bilan de Calibration")
    st.caption(
        f"{counts['usable']} / {counts['total']} images exploitables ; {counts['unusable']} non exploitables."
    )
    if not final:
        st.caption(f"Lot Test final réservé : {report['reserved_count']} image(s).")
    st.dataframe(
        [{COUNT_LABELS[key]: value for key, value in counts.items()}],
        hide_index=True,
        width="stretch",
    )
    people = cast(list[dict[str, object]], report["per_person"])
    st.subheader("Résultats par identité attendue")
    st.dataframe(
        [
            {
                "Identité": row["identity"],
                **{label: row[key] for key, label in COUNT_LABELS.items()},
            }
            for row in people
        ],
        hide_index=True,
        width="stretch",
    )
    st.subheader("Résultats par image")
    rows = cast(list[dict[str, object]], report["rows"])
    st.dataframe(
        [
            {
                "Image": row["image"],
                "Attendu": row["expected"],
                "Décision": row["status"],
                "Motif": row.get("reason"),
                "Nom reconnu": row["identity"],
                "Meilleur candidat": row["candidate"],
                "Similarité cosinus": row["similarity"],
                "Deuxième similarité": row["second_similarity"],
                "Séance": row["session"],
            }
            for row in rows
        ],
        hide_index=True,
        width="stretch",
    )
