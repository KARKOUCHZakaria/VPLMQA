from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import (
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


OUTPUT = Path("output/pdf/scenarios_test_recherche_vplm.pdf")


SCENARIOS = [
    (
        "Recherche simple par mot-cle",
        "Verifier que la recherche globale retourne des resultats pertinents.",
        ["Projet", "Document", "Dossier Actions", "Caracteristiques", "ECO", "AU_2755", "CY_0582", "vplm"],
        "Les resultats affiches doivent contenir des objets lies au mot-cle saisi.",
    ),
    (
        "Recherche simple par classe d'objet",
        "Verifier le filtrage par classe d'objet.",
        ["Document Qualite", "Document SAV", "Donnees achats", "Donnees de base", "Donnees production", "Projet", "Dossier Actions"],
        "Les resultats doivent correspondre a la classe selectionnee.",
    ),
    (
        "Recherche simple avec classe et texte",
        "Verifier la combinaison d'une classe et d'un mot-cle.",
        [
            "Classe Projet + texte CY",
            "Classe Projet + texte ECO",
            "Classe Document Qualite + texte document",
            "Classe Dossier Actions + texte action",
            "Classe Donnees achats + texte achat",
        ],
        "Les resultats doivent respecter la classe choisie et contenir le texte recherche.",
    ),
    (
        "Recherche avancee par classe",
        "Verifier l'ajout d'un critere simple dans la recherche avancee.",
        ["Classe contient Projet", "Classe contient Document", "Classe contient Dossier", "Classe contient Donnees", "Classe contient Article"],
        "Les objets retournes doivent correspondre au type de classe recherche.",
    ),
    (
        "Recherche avancee par designation",
        "Verifier la recherche sur le nom fonctionnel de l'objet.",
        ["Designation contient ECO", "Designation contient Document", "Designation contient Actions", "Designation contient Projet", "Designation contient TEST"],
        "La colonne Designation doit contenir la valeur saisie.",
    ),
    (
        "Recherche avancee par reference",
        "Verifier la recherche avec des references exactes ou partielles.",
        ["Reference contient AU", "Reference contient CY", "Reference contient IN", "Reference contient MODEL", "Reference contient ECO", "Reference contient 2755"],
        "La colonne Reference doit contenir la valeur saisie.",
    ),
    (
        "Recherche avancee par statut",
        "Verifier le filtrage selon l'etat de l'objet.",
        ["Statut contient Initial", "Statut contient En cours", "Statut contient Valide", "Statut contient Obsolete"],
        "Les objets retournes doivent avoir le statut demande, ou aucun resultat si aucun objet ne correspond.",
    ),
    (
        "Recherche avancee par createur",
        "Verifier la recherche selon l'utilisateur createur.",
        ["Createur contient vplm", "Createur contient admin", "Createur contient ADMINISTRATEUR", "Createur contient user"],
        "Les resultats doivent correspondre aux objets crees par l'utilisateur recherche.",
    ),
    (
        "Recherche avancee par date de creation",
        "Verifier les operateurs de date.",
        [
            "Date creation a la date du 17/07/2026",
            "Date creation a la date du 20/07/2022",
            "Date creation apres le 01/01/2022",
            "Date creation avant le 31/12/2024",
        ],
        "Les resultats doivent respecter la periode ou la date selectionnee.",
    ),
    (
        "Cocktail classe + statut",
        "Verifier une combinaison simple avec ET.",
        [
            "Classe contient Projet ET Statut contient Initial",
            "Classe contient Projet ET Statut contient En cours",
            "Classe contient Document ET Statut contient Initial",
            "Classe contient Dossier Actions ET Statut contient Initial",
        ],
        "Les resultats doivent respecter les deux conditions en meme temps.",
    ),
    (
        "Cocktail reference + designation",
        "Verifier la coherence entre deux champs textuels.",
        [
            "Reference contient CY ET Designation contient Projet",
            "Reference contient AU ET Designation contient ECO",
            "Reference contient IN ET Designation contient Vue",
            "Reference contient MODEL ET Designation contient Evolution",
        ],
        "Les resultats doivent correspondre a la fois a la reference et a la designation.",
    ),
    (
        "Cocktail classe + createur + date",
        "Tester une recherche avancee plus realiste.",
        [
            "Classe contient Projet ET Createur contient vplm ET Date creation a la date du 17/07/2026",
            "Classe contient Document ET Createur contient vplm ET Date creation apres le 01/01/2022",
            "Classe contient Dossier Actions ET Createur contient vplm ET Date creation avant le 31/12/2024",
        ],
        "Les resultats doivent etre coherents avec les trois criteres.",
    ),
    (
        "Groupe de conditions avec ET",
        "Verifier les groupes logiques.",
        [
            "Groupe 1: Classe contient Projet + Statut contient Initial",
            "Groupe 2: Createur contient vplm + Date creation apres le 01/01/2022",
        ],
        "Le systeme doit appliquer correctement les conditions groupees.",
    ),
    (
        "Groupe de conditions avec OU",
        "Verifier la recherche avec alternatives.",
        [
            "Classe contient Projet OU Classe contient Document",
            "Statut contient Initial OU Statut contient En cours",
            "Reference contient CY OU Reference contient AU",
        ],
        "Les resultats peuvent correspondre a l'une ou l'autre condition.",
    ),
    (
        "Recherche negative",
        "Verifier le comportement lorsqu'aucun resultat n'est attendu.",
        ["Reference contient ZZZ_NOT_FOUND_999", "Designation contient Objet inexistant test", "Createur contient unknown_user", "Classe contient Classe inexistante"],
        "Le systeme affiche aucun resultat ou un message clair, sans erreur technique.",
    ),
    (
        "Recherche avec caracteres speciaux",
        "Verifier la robustesse des champs texte.",
        ["Reference contient CY_0582", "Reference contient AU_2755", "Designation contient ECO :", "Designation contient VTT SHOCKWAVE (SolidWorks)", "Designation contient Projet de modification"],
        "Les caracteres speciaux, espaces et parentheses ne doivent pas casser la recherche.",
    ),
    (
        "Recherche avec accents",
        "Verifier la gestion des accents francais.",
        ["Designation contient Caracteristiques", "Designation contient Evolution", "Classe contient Donnees", "Classe contient Qualite"],
        "La recherche doit fonctionner correctement avec les accents.",
    ),
    (
        "Pagination apres recherche",
        "Verifier que les resultats restent coherents sur plusieurs pages.",
        ["Classe contient Document", "Passer a la page suivante", "Revenir a la premiere page"],
        "La pagination fonctionne et les filtres restent appliques.",
    ),
    (
        "Tri apres recherche",
        "Verifier le tri des resultats filtres.",
        ["Trier par Reference", "Trier par Designation", "Trier par Statut", "Trier par Createur", "Trier par Date creation"],
        "Le tri doit s'appliquer sans perdre les criteres de recherche.",
    ),
    (
        "Ouverture d'un resultat",
        "Verifier qu'un resultat peut etre consulte apres recherche.",
        ["Chercher CY_0582 puis ouvrir la ligne", "Chercher AU_2755 puis ouvrir la ligne", "Chercher Dossier Actions puis ouvrir un resultat"],
        "La fiche objet s'ouvre avec reference, designation, statut, createur, dates et classe.",
    ),
]


def on_page(canvas, doc):
    canvas.saveState()
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#666666"))
    canvas.drawString(2 * cm, 1.2 * cm, "Scenarios de test manuel - Recherche VPLM")
    canvas.drawRightString(A4[0] - 2 * cm, 1.2 * cm, f"Page {doc.page}")
    canvas.restoreState()


def build_pdf():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(OUTPUT),
        pagesize=A4,
        rightMargin=1.8 * cm,
        leftMargin=1.8 * cm,
        topMargin=1.8 * cm,
        bottomMargin=1.8 * cm,
    )

    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "TitleCustom",
        parent=styles["Title"],
        fontName="Helvetica-Bold",
        fontSize=18,
        leading=22,
        textColor=colors.HexColor("#1f2937"),
        spaceAfter=14,
    )
    intro = ParagraphStyle(
        "Intro",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=10,
        leading=14,
        textColor=colors.HexColor("#374151"),
        spaceAfter=10,
    )
    h2 = ParagraphStyle(
        "ScenarioTitle",
        parent=styles["Heading2"],
        fontName="Helvetica-Bold",
        fontSize=12,
        leading=15,
        textColor=colors.HexColor("#111827"),
        spaceBefore=8,
        spaceAfter=5,
    )
    body = ParagraphStyle(
        "Body",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=9,
        leading=12,
        textColor=colors.HexColor("#111827"),
    )
    small = ParagraphStyle(
        "Small",
        parent=body,
        fontSize=8.5,
        leading=11,
    )

    story = [
        Paragraph("Scenarios de test manuel - Recherche VPLM", title),
        Paragraph(
            "Ce document propose une premiere serie de scenarios manuels pour tester la recherche simple et la recherche avancee dans VPLM. "
            "Les inputs sont volontairement varies afin d'explorer les classes, attributs, operateurs, dates, statuts et combinaisons de conditions.",
            intro,
        ),
        Spacer(1, 8),
    ]

    for index, (name, objective, inputs, expected) in enumerate(SCENARIOS, start=1):
        story.append(Paragraph(f"Scenario {index} : {name}", h2))
        data = [
            [Paragraph("<b>Objectif</b>", small), Paragraph(objective, small)],
            [Paragraph("<b>Inputs a tester</b>", small), Paragraph("<br/>".join(f"- {item}" for item in inputs), small)],
            [Paragraph("<b>Resultat attendu</b>", small), Paragraph(expected, small)],
        ]
        table = Table(data, colWidths=[3.3 * cm, 14.1 * cm], hAlign="LEFT")
        table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#f3f4f6")),
            ("BOX", (0, 0), (-1, -1), 0.4, colors.HexColor("#d1d5db")),
            ("INNERGRID", (0, 0), (-1, -1), 0.25, colors.HexColor("#e5e7eb")),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (-1, -1), 6),
            ("RIGHTPADDING", (0, 0), (-1, -1), 6),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ]))
        story.append(table)
        story.append(Spacer(1, 5))
        if index == 10:
            story.append(PageBreak())

    doc.build(story, onFirstPage=on_page, onLaterPages=on_page)


if __name__ == "__main__":
    build_pdf()
    print(OUTPUT.resolve())
