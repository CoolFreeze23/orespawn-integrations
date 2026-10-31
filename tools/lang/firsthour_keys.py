"""The first hour's translate keys (firsthour group, OI 0.10.0), with the cards' EN and pt-BR text.

Writes _lang_fragments/0.10.0/firsthour.keys.json (key -> "card | SURFACE | where | EN first words"),
firsthour.en_us.json and firsthour.pt_br.json, and checks the spine's text limits:
signs 4 lines of <=15 characters, chat/actionbar/title <=90 characters (pt-BR <=103), a book page <=120 words.
Lines marked "fresh" have no card line and were written in the spine's voices.

Run: python tools/lang/firsthour_keys.py
"""
import json
import os
import sys

S = "orespawn_integrations.senha."
L = "orespawn_integrations.princes_letter."
F = "orespawn_integrations.first_fall."
O = "orespawn_integrations.odete_campo."

# (key, card, surface, where, en, pt_br)
KEYS = [
    # The Priority Ticket
    (S + "handover", "system-senha-da-primeira-hora", "ACTIONBAR", "each handover (arg: the two-digit number)",
     "Ticket P%s. Priority service: Hatchling.", "Senha P%s. Atendimento preferencial: Filhote."),
    (S + "last_call", "system-senha-da-primeira-hora", "CHAT", "P09 at the Registry",
     "Ticket P09. Last priority service. From here on, the normal queue.",
     "Senha P09. Último atendimento preferencial. Daqui pra frente, fila normal."),
    (S + "out_of_turn", "system-senha-da-primeira-hora", "CHAT", "P09 reached before its number",
     "You came before your number. Priority goes first.", "Chegou antes da senha. Preferencial passa na frente."),
    (S + "pin.fall", "system-senha-da-primeira-hora", "CHAT", "JourneyMap gold pin name", "Fall", "Queda"),
    (S + "pin.registry", "system-senha-da-primeira-hora", "CHAT", "JourneyMap gold pin name", "Registry", "Registro"),
    (S + "pin.lodge", "system-senha-da-primeira-hora", "CHAT", "JourneyMap gold pin name (0.10.1)", "Lodge", "Pavilhão"),
    (S + "catalogue_title", "system-senha-da-primeira-hora", "ITEM", "the Catalogue's name (P09)",
     "The Treasury's Catalogue", "O Catálogo do Tesouro"),
    (S + "notebook_fallback", "system-senha-da-primeira-hora", "ACTIONBAR", "P10 without the Mestre",
     "There was an old notebook at the bottom of your pocket. Someone else's handwriting.",
     "Tinha um caderno velho no fundo do bolso. Letra de outra pessoa."),
    (S + "closed", "system-senha-da-primeira-hora", "ACTIONBAR", "the queue closes (once)",
     "Priority queue closed. Welcome to the normal line.",
     "Atendimento preferencial encerrado. Agora é fila normal, igual todo mundo."),

    # The Prince's Letter (the Royal Summons)
    (L + "title", "event-princes-letter", "ITEM", "the Summons' name", "The Royal Summons", "A Convocação Real"),
    (L + "author", "event-princes-letter", "ITEM", "the Summons' author line", "the Prince", "o Príncipe"),
    (L + "page1", "event-princes-letter", "BOOK", "page 1, the letter",
     "To whatever hatched: the throne is empty. The court is open to applicants. Bring snacks. Sincerely, the Prince.",
     "Saudações, ó Coisa Que Chocou. O Trono encontra-se Vago e a Corte aceita Candidaturas. Tragam salgadinho. "
     "Atenciosamente, o Príncipe. P.S.: pão de queijo também serve."),
    (L + "ps_mark", "event-princes-letter", "BOOK", "page 2, before the P.S. (fresh: the card's own marks)", "P.S.", "P.S.:"),
    (L + "pps_mark", "event-princes-letter", "BOOK", "page 2, before the P.P.S. (fresh: the card's own marks)",
     "P.P.S.", "P.P.S.:"),
    (L + "ps.origin.saci", "event-princes-letter", "BOOK", "page 2, P.S. of the Saci origin",
     "Please give back the spoon.", "Devolve a colher, por favor. Sei que foi você."),
    (L + "ps.origin.royal_blood", "event-princes-letter", "BOOK", "page 2, P.S. of the Royal Blood origin",
     "Cousin? Write back!", "Primo(a)? Me responde!"),
    (L + "ps.origin.kraken_spawn", "event-princes-letter", "BOOK", "page 2, P.S. of the Kraken Spawn origin",
     "Don't tell the Admiral I wrote.", "Não conta pra Almirante que eu escrevi."),
    (L + "ps.group.1", "event-princes-letter", "BOOK", "page 2, P.S. of label group 1 Classic",
     "You're normal? I'm jealous.", "Você é normal? Que inveja."),
    (L + "ps.group.3", "event-princes-letter", "BOOK", "page 2, P.S. of label group 3 Brazil",
     "Mother said your lot were here before us. True?", "A Mamãe dizia que vocês eram daqui antes da gente. É verdade?"),
    (L + "ps.group.4", "event-princes-letter", "BOOK", "page 2, P.S. of label group 4 Nature",
     "Do you talk to plants? Mother wants to know.", "Você fala com planta? É a Mamãe que quer saber."),
    (L + "ps.group.5", "event-princes-letter", "BOOK", "page 2, P.S. of label group 5 Creatures and constructs",
     "Beast or machine? Doesn't matter, write back.", "Você é bicho ou máquina? Tanto faz, escreve."),
    (L + "ps.group.6", "event-princes-letter", "BOOK", "page 2, P.S. of label group 6 Dragons",
     "Cousin? Hi, I guess.", "Primo(a)? Sei lá. Oi."),
    (L + "ps.group.7", "event-princes-letter", "BOOK", "page 2, P.S. of label group 7 Undead and shadows",
     "Have you died? What's it like? Serious question.", "Você já morreu? Como é? Pergunta séria."),
    (L + "ps.group.8", "event-princes-letter", "BOOK", "page 2, P.S. of label group 8 Mages",
     "Teach me a spell? A small one.", "Me ensina uma mágica? Uma pequenininha."),
    (L + "ps.group.9", "event-princes-letter", "BOOK", "page 2, P.S. of label group 9 Martial masters",
     "You hit bosses barehanded? Take me along.", "Você bate em chefão sem arma? Me leva junto."),
    (L + "ps.group.10", "event-princes-letter", "BOOK", "page 2, P.S. of label group 10 Others (and our other CrazyCraft origins)",
     "What are you again? Answer overleaf.", "Você é o quê, mesmo? Responde no verso."),
    (L + "hint_ants", "event-princes-letter", "BOOK", "page 2, the R4 hint",
     "Ask the ants. They know the roads.", "Pergunta pras formigas. Elas sabem os caminhos."),
    (L + "pps_first_fall", "event-princes-letter", "BOOK", "page 2, the First Fall's P.P.S.",
     "If something falls near you, it's yours. Finders keepers, as your little egg will tell you. "
     "(The Treasury says otherwise. The Treasury is closed.)",
     "Se cair alguma coisa perto de você, é sua. Achado não é roubado, que o diga o seu ovinho. "
     "(A Tesouraria discorda. A Tesouraria tá fechada.)"),
    (L + "page3_form_7b", "quest-regency-application", "BOOK", "page 3, Form 7-B",
     "FORM 7-B. Application for the Regency. Requirements: (1) one Crown, any condition; (2) the Ledger's Spine; "
     "(3) one Queen Scale (taken by force or found by luck); (4) the votes of twelve Electors. Deliver in person at "
     "any Royal Altar. Processing time: one (1) boss, or none, if Her Majesty approves of the candidate. Do not fold. "
     "The paper crown is a separate item.",
     "FICHA 7-B. Requerimento de Regência. Requisitos: (1) uma Coroa, em qualquer estado de conservação; (2) a Lombada "
     "do Livro-Razão; (3) uma Escama da Rainha (tomada à força ou achada por sorte); (4) os votos de doze Eleitores. "
     "Entregar pessoalmente em qualquer Altar Real. Prazo de análise: um (1) chefe; dispensar-se-á o chefe caso Sua "
     "Majestade aprove o(a) candidato(a). Não dobrar. A coroa de papel é item à parte."),

    # The First Fall
    (F + "warning", "event-first-fall", "ACTIONBAR", "the Fall's warning",
     "Something gold is falling. It isn't a star. Stars don't whistle.",
     "Tem um troço dourado caindo do céu. Não é estrela. Estrela não assobia."),
    (F + "consignee", "event-first-fall", "ACTIONBAR", "someone else touches an unopened crate",
     "This crate has a consignee. It isn't you.", "Esse caixote tem destinatário. Não é você."),
    (F + "sign.line1", "event-first-fall", "SIGN", "crate label, line 1", "LOT 9/1,129", "LOTE 9/1.129"),
    (F + "sign.line2", "event-first-fall", "SIGN", "crate label, line 2", "spyglass,", "luneta, balde"),
    (F + "sign.line3", "event-first-fall", "SIGN", "crate label, line 3", "bucket, change", "e trocado"),
    (F + "sign.line3_no_coin", "event-first-fall", "SIGN", "crate label, line 3, the crater-less crate",
     "bucket, no coin", "e sem troco"),
    (F + "sign.line4", "event-first-fall", "SIGN", "crate label, line 4", "CONSIGNEE: —", "DESTINATÁRIO: —"),
    (F + "sign_late.line1", "event-first-fall", "SIGN", "Back-Dated crate label, line 1", "LATE DELIVERY", "ATRASO: 77 anos"),
    (F + "sign_late.line2", "event-first-fall", "SIGN", "Back-Dated crate label, line 2", "77 years late.", "Desculpe o"),
    (F + "sign_late.line3", "event-first-fall", "SIGN", "Back-Dated crate label, line 3", "We apologise.", "transtorno."),
    (F + "sign_late.line4", "event-first-fall", "SIGN", "Back-Dated crate label, line 4", "— the Treasury", "— a Tesouraria"),
    (F + "glowstone_lore", "event-first-fall", "ITEM", "lore on the crate's 4 glowstone",
     "ITEM 3: Hatchery chute lamps. 4 of 10.", "ITEM 3: lâmpadas da calha da Chocadeira. 4 de 10."),
    (F + "item5_slip", "event-first-fall", "ITEM", "lore on the twin's slip (ITEM 5)",
     "ITEM 5: 1 small egg, misfiled. It was meant to be in a tree.",
     "ITEM 5: 1 ovinho, extraviado. Era pra estar numa árvore."),
    (F + "item6_notice", "event-first-fall", "ITEM", "lore on the Back-Dated crate's notice (ITEM 6)",
     "ITEM 6: one notice. The recipes have been put back. Nothing else has.",
     "ITEM 6: um aviso. As receitas foram devolvidas. O resto, não."),

    # The Field Odete
    (O + "name", "npc-odete-de-campo", "CHAT", "her name before her chat lines (fresh)", "Odete", "Odete"),
    (O + "greeting.morning", "npc-odete-de-campo", "CHAT", "greeting, cc_turno 0",
     "Good morning. This fall is not in the system. Neither is the applicant. Let's fix both.",
     "Bom dia. Esta queda não consta no sistema. O(a) requerente também não. Vamos regularizar as duas."),
    (O + "greeting.afternoon", "npc-odete-de-campo", "CHAT", "greeting, cc_turno 1",
     "Good afternoon. This fall is not in the system. Neither is the applicant. Let's fix both.",
     "Boa tarde. Esta queda não consta no sistema. O(a) requerente também não. Vamos regularizar as duas."),
    (O + "greeting.evening", "npc-odete-de-campo", "CHAT", "greeting, cc_turno 2",
     "Good evening. This fall is not in the system. Neither is the applicant. Let's fix both.",
     "Boa noite. Esta queda não consta no sistema. O(a) requerente também não. Vamos regularizar as duas."),
    (O + "greeting.veteran", "npc-odete-de-campo", "CHAT", "greeting, a veteran's Back-Dated Fall",
     "Old applicant, new file. The fall ran late too: seventy-seven years.",
     "Requerente antigo(a), processo novo. A queda também atrasou: setenta e sete anos."),
    (O + "origin.royal_blood", "npc-odete-de-campo", "CHAT", "Royal Blood origin",
     "Your Highness... I mean, applicant.", "Vossa Alteza... digo, requerente."),
    (O + "origin.saci", "npc-odete-de-campo", "CHAT", "Saci origin", "Just checking. Force of habit.",
     "Só conferindo. Força do hábito."),
    (O + "origin.curupira", "npc-odete-de-campo", "CHAT", "Curupira origin",
     "Feet backwards. Do I write that you came or that you left?",
     "Pé virado pra trás. Eu anoto que chegou ou que foi embora?"),
    (O + "origin.time_lord", "npc-odete-de-campo", "CHAT", "Time Lord origin", "Weren't you here tomorrow?",
     "Você não esteve aqui amanhã?"),
    (O + "label.1", "npc-odete-de-campo", "CHAT", "Form 7-B label, group 1 Classic",
     "Hatchling, assorted, classic edition.", "Filhote, sortido, edição clássica."),
    (O + "label.3", "npc-odete-de-campo", "CHAT", "Form 7-B label, group 3 Brazil",
     "Hatchling... of the Land? Not our department.", "Filhote... da Terra? Não é do nosso departamento."),
    (O + "label.4", "system-etiqueta-do-ovo", "CHAT", "Form 7-B label, group 4 Nature",
     "Forest folk: fill in triplicate.", "Gente da mata: preencher em três vias."),
    (O + "label.5", "system-etiqueta-do-ovo", "CHAT", "Form 7-B label, group 5 Creatures and constructs",
     "Is the applicant movable or immovable property?", "O(a) requerente é móvel ou imóvel?"),
    (O + "label.6", "system-etiqueta-do-ovo", "CHAT", "Form 7-B label, group 6 Dragons",
     "Dragon hatchling. Family?", "Filhote de dragão. Parente?"),
    (O + "label.7", "npc-odete-de-campo", "CHAT", "Form 7-B label, group 7 Undead",
     "Deceased applicant: present a death certificate.",
     "Requerente falecido(a): favor apresentar certidão de óbito."),
    (O + "label.8", "system-etiqueta-do-ovo", "CHAT", "Form 7-B label, group 8 Mages",
     "Hatchling, school to be decided.", "Filhote, escola a definir."),
    (O + "label.9", "system-etiqueta-do-ovo", "CHAT", "Form 7-B label, group 9 Martial masters",
     "Hatchling, martial artist: please don't break the counter.",
     "Filhote, arte marcial: favor não quebrar o balcão."),
    (O + "label.10", "system-etiqueta-do-ovo", "CHAT", "Form 7-B label, group 10 Others (and our other CrazyCraft origins)",
     "Unclassified. Even we can't read you.", "Sem classificação. Nem a gente sabe ler você."),
    (O + "r2", "npc-odete-de-campo", "CHAT", "the 'Now you exist' line (grants R2)",
     "Sign here. No, here. 'Eggs, assorted, one lot'... there. Now you exist. Provisionally.",
     "Assina aqui. Não, aqui. 'Ovos, sortidos, um lote'... pronto. Agora você existe. Em caráter provisório."),
    (O + "file_number", "npc-odete-de-campo", "CHAT", "the file number",
     "I'll open a file for the applicant. Number... 0001-77.",
     "Vou abrir um processo pro(a) requerente. Número... 0001-77."),
    (O + "casket_tip", "npc-odete-de-campo", "CHAT", "the casket tip (no scrap spent yet)",
     "Scrap sitting in your pocket? Eight planks and it's a casket, sized to your rank.",
     "Retalho parado no bolso? Oito tábuas e vira arca, do tamanho da sua patente."),
    (O + "notebook", "npc-odete-de-campo", "CHAT", "handing over the Field Notebook",
     "Anything you spot from far off, write it down. The Treasury lost the map with everything else.",
     "Tudo que avistar de longe, anota. A Tesouraria perdeu o mapa junto com o resto."),
    (O + "leaving", "npc-odete-de-campo", "CHAT", "her parting line, with the Registry pin",
     "The Registry desk is that way. Take a number: priority is today.",
     "O balcão do Cartório é pra lá. Pegue uma senha: preferencial é hoje."),
    (O + "no_village", "npc-odete-de-campo", "CHAT", "no desk or village near (0.10.0)",
     "The Registry? Far. Follow the Four Ways signs back at the egg.",
     "O Cartório? Longe. Siga as placas dos Quatro Caminhos, lá no ovo."),
    (O + "refurbished", "npc-odete-de-campo", "CHAT", "the warning, when the pin is a village",
     "If the office isn't there, it's being refurbished. Come back tomorrow.",
     "Se o cartório não estiver lá, está em reforma. Volte amanhã."),
]

LIMITS = {"CHAT": (90, 103), "ACTIONBAR": (90, 103), "TITLE": (90, 103), "SIGN": (15, 15), "HANGING": (10, 10)}


def check():
    problems = []
    seen = set()
    for key, card, surface, where, en, pt in KEYS:
        if key in seen:
            problems.append("duplicate " + key)
        seen.add(key)
        if surface in LIMITS:
            en_max, pt_max = LIMITS[surface]
            if len(en) > en_max:
                problems.append(f"{key}: EN {len(en)} > {en_max}")
            if len(pt) > pt_max:
                problems.append(f"{key}: pt-BR {len(pt)} > {pt_max}")
        if surface == "BOOK":
            for lang, text in (("EN", en), ("pt-BR", pt)):
                if len(text.split()) > 120:
                    problems.append(f"{key}: {lang} {len(text.split())} words > 120")
    return problems


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "_lang_fragments", "0.10.0")
    os.makedirs(root, exist_ok=True)
    problems = check()
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    notes = {k: f"{card} | {surface} | {where} | {en[:40]}" for k, card, surface, where, en, pt in KEYS}
    files = {
        "firsthour.keys.json": notes,
        "firsthour.en_us.json": {k: en for k, card, surface, where, en, pt in KEYS},
        "firsthour.pt_br.json": {k: pt for k, card, surface, where, en, pt in KEYS},
    }
    for name, data in files.items():
        with open(os.path.join(root, name), "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print(f"{len(KEYS)} keys written")


if __name__ == "__main__":
    main()
