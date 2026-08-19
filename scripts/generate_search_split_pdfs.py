from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


OUTPUT_DIR = Path("output/pdf")
SIMPLE_OUTPUT = OUTPUT_DIR / "recherche_simple_vplm_cas_de_test.pdf"
ADVANCED_OUTPUT = OUTPUT_DIR / "recherche_avancee_vplm_cas_de_test.pdf"


SIMPLE_CASES = [
    {
        "title": "Recherche simple - Reference exacte d'un projet",
        "class": "Projet",
        "inputs": ["Reference: PRJ-00005", "Designation: vide", "Mot-cle global: PRJ-00005"],
        "expected": "Le resultat doit afficher le projet dont la reference correspond exactement a PRJ-00005.",
    },
    {
        "title": "Recherche simple - Reference partielle d'un projet",
        "class": "Projet",
        "inputs": ["Reference: PRJ", "Designation: vide", "Mot-cle global: PRJ"],
        "expected": "La liste doit retourner les projets contenant PRJ dans la reference.",
    },
    {
        "title": "Recherche simple - Designation projet",
        "class": "Projet",
        "inputs": ["Reference: vide", "Designation: Alpha Phase", "Mot-cle global: Alpha Phase"],
        "expected": "Les projets dont la designation contient Alpha Phase doivent etre visibles.",
    },
    {
        "title": "Recherche simple - Reference et designation projet",
        "class": "Projet",
        "inputs": ["Reference: CY_2795", "Designation: System Integration", "Mot-cle global: CY_2795 System Integration"],
        "expected": "Le systeme doit retourner le projet qui correspond a la reference et a la designation saisies.",
    },
    {
        "title": "Recherche simple - Article concu par reference",
        "class": "Article concu",
        "inputs": ["Reference: PRT103821", "Designation: vide", "Mot-cle global: PRT103821"],
        "expected": "L'article concu avec la reference PRT103821 doit apparaitre dans les resultats.",
    },
    {
        "title": "Recherche simple - Article concu par designation",
        "class": "Article concu",
        "inputs": ["Reference: vide", "Designation: TEST_PRT103821", "Mot-cle global: TEST_PRT103821"],
        "expected": "L'article concu doit etre retrouve a partir de sa designation.",
    },
    {
        "title": "Recherche simple - Reference et designation d'un article",
        "class": "Article concu",
        "inputs": ["Reference: PRTPR001", "Designation: TEST_YBL_001", "Mot-cle global: PRTPR001 TEST_YBL_001"],
        "expected": "Le resultat doit garder une coherence entre la reference et la designation de l'article.",
    },
    {
        "title": "Recherche simple - Piece par designation",
        "class": "Piece SLW",
        "inputs": ["Reference: vide", "Designation: Piece B", "Mot-cle global: Piece B"],
        "expected": "Les pieces dont la designation contient Piece B doivent apparaitre.",
    },
    {
        "title": "Recherche simple - Piece par reference partielle",
        "class": "Piece SLW",
        "inputs": ["Reference: TEST_YBL", "Designation: vide", "Mot-cle global: TEST_YBL"],
        "expected": "La recherche doit retourner les pieces dont la reference commence ou contient TEST_YBL.",
    },
    {
        "title": "Recherche simple - Document commercial",
        "class": "Document Commercial",
        "inputs": ["Reference: 001846", "Designation: document commercial", "Mot-cle global: 001846 document"],
        "expected": "Le document commercial correspondant doit etre visible dans la grille.",
    },
    {
        "title": "Recherche simple - Document qualite",
        "class": "Document Qualite",
        "inputs": ["Reference: DOC", "Designation: Qualite", "Mot-cle global: DOC Qualite"],
        "expected": "Les documents qualite correspondant aux valeurs saisies doivent etre retournes.",
    },
    {
        "title": "Recherche simple - Document SAV",
        "class": "Document SAV",
        "inputs": ["Reference: SAV", "Designation: intervention", "Mot-cle global: SAV intervention"],
        "expected": "La liste doit afficher uniquement les documents SAV pertinents.",
    },
    {
        "title": "Recherche simple - Donnees achats",
        "class": "Donnees achats",
        "inputs": ["Reference: ACH", "Designation: fournisseur", "Mot-cle global: ACH fournisseur"],
        "expected": "Les donnees achats correspondant au contexte fournisseur doivent etre affichees.",
    },
    {
        "title": "Recherche simple - Donnees production",
        "class": "Donnees production",
        "inputs": ["Reference: PROD", "Designation: fabrication", "Mot-cle global: PROD fabrication"],
        "expected": "Les donnees de production liees a la fabrication doivent apparaitre.",
    },
    {
        "title": "Recherche simple - Donnees de base",
        "class": "Donnees de base",
        "inputs": ["Reference: BASE", "Designation: article standard", "Mot-cle global: BASE article standard"],
        "expected": "Le filtrage doit remonter les donnees de base coherentes avec la saisie.",
    },
    {
        "title": "Recherche simple - Dossier actions",
        "class": "Dossier Actions",
        "inputs": ["Reference: ACT", "Designation: Actions", "Mot-cle global: ACT Actions"],
        "expected": "Les dossiers d'actions doivent etre retrouves par reference ou designation.",
    },
    {
        "title": "Recherche simple - Dossier des articles impactes",
        "class": "Dossier des articles impactes",
        "inputs": ["Reference: IMP", "Designation: articles impactes", "Mot-cle global: IMP articles impactes"],
        "expected": "La recherche doit retourner les dossiers lies aux articles impactes.",
    },
    {
        "title": "Recherche simple - Caracteristiques",
        "class": "Caracteristiques",
        "inputs": ["Reference: CAR", "Designation: caracteristique", "Mot-cle global: CAR caracteristique"],
        "expected": "Les objets de type caracteristiques doivent etre affiches si les valeurs existent.",
    },
    {
        "title": "Recherche simple - Recherche sans resultat",
        "class": "Toutes classes",
        "inputs": ["Reference: ZZ_NOT_FOUND_999", "Designation: Objet inexistant test", "Mot-cle global: ZZ_NOT_FOUND_999"],
        "expected": "Le systeme doit afficher une grille vide ou un message clair sans erreur technique.",
    },
    {
        "title": "Recherche simple - Sensibilite aux espaces",
        "class": "Toutes classes",
        "inputs": ["Reference: '  PRJ-00005  '", "Designation: '  Alpha Phase  '", "Mot-cle global: espaces avant et apres"],
        "expected": "Les espaces inutiles ne doivent pas empecher la recherche de fonctionner.",
    },
]


ADVANCED_CASES = [
    {
        "title": "Recherche avancee - Document Plan P070",
        "logic": "ET",
        "conditions": ["Classe contient Document Plan", "Reference contient P070"],
        "expected": "La grille doit afficher les documents plans dont la reference contient P070.",
    },
    {
        "title": "Recherche avancee - Document Plan P070 avec designation",
        "logic": "ET",
        "conditions": ["Classe contient Document Plan", "Reference contient P070", "Designation contient Moyeu arriere complet"],
        "expected": "Le resultat doit contenir le document plan P070 avec la designation Moyeu arriere complet.",
    },
    {
        "title": "Recherche avancee - Article concu P054",
        "logic": "ET",
        "conditions": ["Classe contient Article concu", "Reference contient P054", "Designation contient Bras fourche"],
        "expected": "L'article concu P054 doit apparaitre avec la designation Bras fourche.",
    },
    {
        "title": "Recherche avancee - Articles concus par designation fourche",
        "logic": "OU",
        "conditions": ["Designation contient Bras fourche", "Designation contient Extremite fourche"],
        "expected": "Les resultats doivent inclure les articles dont la designation parle de fourche.",
    },
    {
        "title": "Recherche avancee - Documents SAV ou Document Etude",
        "logic": "OU",
        "conditions": ["Classe contient Document SAV", "Classe contient Document Etude"],
        "expected": "La recherche doit retourner les documents SAV et les documents etude visibles dans la base.",
    },
    {
        "title": "Recherche avancee - Document SAV ANX_001757",
        "logic": "ET",
        "conditions": ["Classe contient Document SAV", "Reference contient ANX_001757", "Designation contient Annexe de commande Octobre"],
        "expected": "Le document SAV ANX_001757 doit etre retrouve avec sa designation.",
    },
    {
        "title": "Recherche avancee - Document Etude DDEF_001699",
        "logic": "ET",
        "conditions": ["Classe contient Document Etude", "Reference contient DDEF_001699", "Designation contient DOSSIER DE PLANS"],
        "expected": "Le document etude DDEF_001699 doit etre visible dans les resultats.",
    },
    {
        "title": "Recherche avancee - Document Production 001759",
        "logic": "ET",
        "conditions": ["Classe contient Document Production", "Reference contient 001759", "Designation contient Adaptation baseline"],
        "expected": "Le document de production 001759 doit apparaitre avec sa designation.",
    },
    {
        "title": "Recherche avancee - Documents attaches au VTT",
        "logic": "ET",
        "conditions": ["Classe contient Document Projet", "Reference contient ANX_001760", "Designation contient Document attache au VTT"],
        "expected": "Le document projet ANX_001760 doit etre retourne.",
    },
    {
        "title": "Recherche avancee - Plan CAO SLW P044",
        "logic": "ET",
        "conditions": ["Classe contient Plan CAO SLW", "Reference contient P044", "Designation contient Biellette equipee"],
        "expected": "Le plan CAO SLW P044 doit apparaitre dans les resultats.",
    },
    {
        "title": "Recherche avancee - Plans CAO crees par agriyard",
        "logic": "ET",
        "conditions": ["Classe contient Plan CAO SLW", "Createur contient agriyard", "Date creation a la date du 10/07/2020"],
        "expected": "Les plans CAO SLW crees par agriyard le 10/07/2020 doivent etre visibles.",
    },
    {
        "title": "Recherche avancee - Plans CAO par designation",
        "logic": "OU",
        "conditions": ["Designation contient Moyeu Avant", "Designation contient Axe pivot", "Designation contient Cadre principal Droit"],
        "expected": "La recherche doit retourner les plans contenant l'une de ces designations.",
    },
    {
        "title": "Recherche avancee - Documents Plan ZS06",
        "logic": "OU",
        "conditions": ["Reference contient ZS06-305", "Reference contient ZS06-109", "Reference contient ZS06-400"],
        "expected": "La recherche doit afficher les documents plans dont la reference commence par ZS06.",
    },
    {
        "title": "Recherche avancee - Document Plan ZS06-400",
        "logic": "ET",
        "conditions": ["Classe contient Document Plan", "Reference contient ZS06-400", "Designation contient DRIVE ASSY-FINAL"],
        "expected": "Le document plan ZS06-400 doit etre retrouve.",
    },
    {
        "title": "Recherche avancee - Objets valides par vplm",
        "logic": "ET",
        "conditions": ["Designation contient Tests de charge ANSIS", "Createur contient vplm", "Statut contient Valide"],
        "expected": "L'objet Tests de charge ANSIS au statut Valide doit apparaitre.",
    },
    {
        "title": "Recherche avancee - Objets archives par designation",
        "logic": "ET",
        "conditions": ["Designation contient Tests de charge ANSIS", "Createur contient vplm", "Statut contient Archive"],
        "expected": "La recherche doit retrouver la version archivee visible dans la grille.",
    },
    {
        "title": "Recherche avancee - Administration par createur",
        "logic": "ET / OU",
        "conditions": ["Groupe 1: Domaine contient Administration", "Groupe 2: Createur contient vplm", "Groupe 3: Note contient Tableau de bord OU Note contient contacts externes"],
        "expected": "Les applications d'administration creees par vplm doivent etre filtrees selon la note.",
    },
    {
        "title": "Recherche avancee - Cocktails articles et plans",
        "logic": "ET / OU",
        "conditions": ["Groupe 1: Classe contient Article concu OU Classe contient Plan CAO SLW", "Groupe 2: Statut contient Initial", "Groupe 3: Reference contient P05 OU Designation contient Cadre"],
        "expected": "Les resultats doivent combiner articles et plans au statut Initial autour des references P05 ou des cadres.",
    },
    {
        "title": "Recherche avancee - Condition negative",
        "logic": "ET",
        "conditions": ["Classe contient Document Plan", "Reference contient ZZ_NOT_FOUND_999"],
        "expected": "Aucun resultat ne doit apparaitre, sans blocage ni erreur applicative.",
    },
    {
        "title": "Recherche avancee - Recherche large controlee",
        "logic": "OU",
        "conditions": ["Designation contient Moyeu", "Designation contient Ressort", "Designation contient Document"],
        "expected": "La recherche peut retourner plusieurs familles d'objets, mais les resultats doivent rester lies aux termes visibles dans la base.",
    },
]


def styles():
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "Title",
            parent=base["Title"],
            fontName="Helvetica-Bold",
            fontSize=21,
            leading=26,
            textColor=colors.HexColor("#111827"),
            spaceAfter=14,
        ),
        "intro": ParagraphStyle(
            "Intro",
            parent=base["BodyText"],
            fontName="Helvetica",
            fontSize=10.5,
            leading=14,
            textColor=colors.HexColor("#374151"),
            spaceAfter=12,
        ),
        "h2": ParagraphStyle(
            "Heading2",
            parent=base["Heading2"],
            fontName="Helvetica-Bold",
            fontSize=12,
            leading=15,
            textColor=colors.HexColor("#111827"),
            spaceBefore=7,
            spaceAfter=5,
        ),
        "cell": ParagraphStyle(
            "Cell",
            parent=base["BodyText"],
            fontName="Helvetica",
            fontSize=8.8,
            leading=11.5,
            textColor=colors.HexColor("#111827"),
        ),
    }


def on_page(canvas, doc, title):
    canvas.saveState()
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#666666"))
    canvas.drawString(2 * cm, 1.1 * cm, title)
    canvas.drawRightString(A4[0] - 2 * cm, 1.1 * cm, f"Page {doc.page}")
    canvas.restoreState()


def make_table(rows, style):
    table = Table(rows, colWidths=[3.4 * cm, 14 * cm], hAlign="LEFT", repeatRows=0)
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
    return table


def build_simple_pdf(style):
    doc = SimpleDocTemplate(
        str(SIMPLE_OUTPUT),
        pagesize=A4,
        rightMargin=2 * cm,
        leftMargin=2 * cm,
        topMargin=2 * cm,
        bottomMargin=1.8 * cm,
    )
    title = "Cas de test manuel - Recherche simple VPLM"
    story = [
        Paragraph(title, style["title"]),
        Paragraph(
            "Ce document regroupe des cas de test riches pour la recherche simple. "
            "Chaque cas propose une classe cible et des inputs differents. "
            "La reference et la designation peuvent etre testees ensemble pour verifier la coherence des resultats.",
            style["intro"],
        ),
    ]
    for index, case in enumerate(SIMPLE_CASES, start=1):
        story.append(Paragraph(f"Cas {index} : {case['title']}", style["h2"]))
        rows = [
            [Paragraph("<b>Classe</b>", style["cell"]), Paragraph(case["class"], style["cell"])],
            [Paragraph("<b>Inputs</b>", style["cell"]), Paragraph("<br/>".join(f"- {value}" for value in case["inputs"]), style["cell"])],
            [Paragraph("<b>Attendu</b>", style["cell"]), Paragraph(case["expected"], style["cell"])],
        ]
        story.append(make_table(rows, style))
        story.append(Spacer(1, 5))
    doc.build(story, onFirstPage=lambda c, d: on_page(c, d, title), onLaterPages=lambda c, d: on_page(c, d, title))


def build_advanced_pdf(style):
    doc = SimpleDocTemplate(
        str(ADVANCED_OUTPUT),
        pagesize=A4,
        rightMargin=2 * cm,
        leftMargin=2 * cm,
        topMargin=2 * cm,
        bottomMargin=1.8 * cm,
    )
    title = "Cas de test manuel - Recherche avancee VPLM"
    story = [
        Paragraph(title, style["title"]),
        Paragraph(
            "Ce document propose des cas de recherche avancee bases sur des combinaisons de criteres. "
            "Les tests couvrent des conditions simples, des groupes logiques ET, des groupes OU et des cocktails de filtres "
            "afin de verifier le comportement de la recherche dans des situations proches d'une utilisation reelle.",
            style["intro"],
        ),
    ]
    for index, case in enumerate(ADVANCED_CASES, start=1):
        story.append(Paragraph(f"Cas {index} : {case['title']}", style["h2"]))
        rows = [
            [Paragraph("<b>Logique</b>", style["cell"]), Paragraph(case["logic"], style["cell"])],
            [Paragraph("<b>Conditions</b>", style["cell"]), Paragraph("<br/>".join(f"- {value}" for value in case["conditions"]), style["cell"])],
            [Paragraph("<b>Attendu</b>", style["cell"]), Paragraph(case["expected"], style["cell"])],
        ]
        story.append(make_table(rows, style))
        story.append(Spacer(1, 5))
    doc.build(story, onFirstPage=lambda c, d: on_page(c, d, title), onLaterPages=lambda c, d: on_page(c, d, title))


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    style = styles()
    build_simple_pdf(style)
    build_advanced_pdf(style)
    print(SIMPLE_OUTPUT.resolve())
    print(ADVANCED_OUTPUT.resolve())


if __name__ == "__main__":
    main()
