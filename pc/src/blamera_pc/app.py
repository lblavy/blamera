"""Point d'entrée de la préparation locale sur PC."""

import streamlit as st
from blamera_pc.evaluation_ui import render_evaluation
from blamera_pc.people_ui import data_directory, render_people


def render() -> None:
    st.title("Blamera — préparation locale")
    workflow = st.radio("Parcours", ["Personnes", "Chats"], index=0, horizontal=True)
    if workflow == "Personnes":
        page = st.radio("Personnes", ["Références", "Évaluation"], horizontal=True)
        if page == "Références":
            render_people()
        else:
            render_evaluation(data_directory())
        st.caption("Galerie locale · reconnaissance et export à venir")
    else:
        st.caption("M4 : préparation des chats, expériences CPU et export à venir.")


render()
