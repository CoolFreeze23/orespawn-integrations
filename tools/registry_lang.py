"""Writes the registry group's lang fragments for 0.10.0 (_lang_fragments/0.10.0/registry.*.json) from one table.

Each row: key, card, surface, where it shows, EN, pt-BR. EN and pt-BR are the cards' lines as written; rows marked
"(fresh)" have no card line and are written in the spine's Odete voice. The spine's text limits are checked here.
Run from the repo root: python tools/registry_lang.py
"""
import json
import os

O = "orespawn_integrations.odete."
F = "orespawn_integrations.odete_campo."
C = "orespawn_integrations.correcoes."
R = "orespawn_integrations.registro.ops."
A = "advancements.orespawn_integrations.correcoes."

ROWS = [
    # The desk Odete (npc-odetes) and her dialog
    (O + "npc.name", "npc-odetes", "DIALOG", "desk Odete's name tag (fresh)", "Odete", "Odete"),
    (O + "npc.title", "npc-odetes", "DIALOG", "desk Odete's title (fresh)", "Registry Clerk", "Escrevente do Cartório"),
    (O + "dialog.title", "npc-odetes", "DIALOG", "desk dialog title (fresh)", "Registry · Window 1", "Cartório · Guichê 1"),
    (O + "dialog.text", "npc-odetes", "DIALOG", "desk dialog text, the averbação's standing line (line 38)",
     "One scrap, one wrong thing, one right thing. Sign here. No, here. Come back tomorrow for the copy.",
     "Um retalho, uma coisa errada, uma coisa certa. Assina aqui. Não, aqui. Volte amanhã pra pegar a cópia."),
    (O + "opcao.averbar", "system-corrections", "OPTION", "desk option (line 14)", "Register a scrap", "Averbar um retalho"),
    (O + "opcao.segunda_via", "system-corrections", "OPTION", "desk option (line 14)", "Collect the copy", "Retirar a segunda via"),
    (O + "opcao.cortesia", "system-corrections", "OPTION", "desk option (line 14)", "Ask for the courtesy", "Pedir a cortesia"),
    (O + "opcao.que_sistema", "npc-odetes", "OPTION", "desk and Field Odete option (line 12)", "What system?", "Que sistema?"),
    (O + "opcao.sair", "npc-odetes", "OPTION", "closes any Odete dialog (fresh)", "Thank you.", "Obrigado(a)."),
    (O + "que_sistema", "npc-odetes", "CHAT", "answer to What system? (line 13)", "The Ledger. It tore lengthwise. Next!",
     "O Livro-Razão. Rasgou no comprido. Próximo!"),
    (O + "averbado", "system-corrections", "CHAT", "a scrap registered (line 15)",
     "Registered. One slip now; the copy's out tomorrow. Next!", "Averbado. Uma guia agora; a cópia sai amanhã. Próximo!"),
    (O + "segunda_via", "system-corrections", "CHAT", "a copy collected (line 16)",
     "Your copy, checked by p.p. B. See? Tomorrow always comes.", "Segunda via, conferida por p.p. B. Viu? Amanhã sempre chega."),
    (O + "cedo", "system-corrections", "CHAT", "the copy asked for too early (line 17)",
     "The copy's tomorrow. Today is still today.", "Segunda via só amanhã. Hoje ainda é hoje."),
    (O + "nada_pendente", "system-corrections", "CHAT", "no copy filed (line 18)",
     "No copy on file under your name.", "Não consta segunda via em seu nome."),
    (O + "sem_retalho", "system-corrections", "CHAT", "Register a scrap with none held (line 19)",
     "No scrap, no registration. Come back with paper.", "Sem retalho não tem averbação. Volte com papel."),
    (O + "sem_cortesia", "system-corrections", "CHAT", "courtesy asked with no perk owed (line 20)",
     "The courtesy is for brokers. It says here you're not one.", "Cortesia é pra corretor. Consta aqui que não é o caso."),
    (O + "primeira_via", "system-corrections", "CHAT", "the first copy collected; %s = the day after filing (line 21)",
     "Checked: p.p. B., day %s. Funny: he always signs with tomorrow's date.",
     "Conferido: p.p. B., dia %s. Engraçado: ele assina sempre com a data de amanhã."),
    (O + "cortesia", "system-corrections", "CHAT", "the courtesy's slips handed over (fresh)",
     "The broker's courtesy. Signed, stamped, free of charge. Next!", "Cortesia de corretor. Assinada, carimbada, sem custas. Próximo!"),
    (O + "cortesia_ja", "system-corrections", "CHAT", "courtesy asked twice the same game day (fresh)",
     "Today's courtesy is already paid. Come back tomorrow.", "A cortesia de hoje já foi paga. Volte amanhã."),
    (O + "via_hoje", "system-corrections", "CHAT", "a copia_hoje clause hands the copy the same day (fresh)",
     "Registered, and the copy's out today. Exceptionally. Next!", "Averbado, e a segunda via sai hoje. Excepcionalmente. Próximo!"),
    (O + "fechado", "system-corrections", "CHAT", "a desk option while the Registry or the Corrections are switched off (fresh)",
     "This window is closed until further notice. Take a number.", "Guichê fechado até segunda ordem. Pegue uma senha."),
    (O + "visto", "npc-odetes", "ITEM", "lore from odete/carimbar; %s = the day (line 44)",
     "Checked: p.p. B. — day %s", "Conferido: p.p. B. — dia %s"),
    # The Cartório's signs (npc-odetes lines 23, 24)
    (O + "placa.cartorio.1", "npc-odetes", "HANGING", "Cartório door sign, line 1 (line 23)", "VILLAGE", "CARTÓRIO"),
    (O + "placa.cartorio.2", "npc-odetes", "HANGING", "Cartório door sign, line 2 (line 23)", "REGISTRY", "DA VILA"),
    (O + "placa.cartorio.3", "npc-odetes", "HANGING", "Cartório door sign, line 3 (line 23)", "Take a", "Pegue sua"),
    (O + "placa.cartorio.4", "npc-odetes", "HANGING", "Cartório door sign, line 4 (line 23)", "number", "senha"),
    (O + "placa.trono.1", "npc-odetes", "SIGN", "throne sign, line 1 (line 24)", "THRONE", "TRONO"),
    (O + "placa.trono.2", "npc-odetes", "SIGN", "throne sign, line 2 (line 24)", "(PROVISIONAL)", "(PROVISÓRIO)"),
    (O + "placa.trono.3", "npc-odetes", "SIGN", "throne sign, line 3 (line 24)", "Do not sit.", "Proibido sentar"),
    (O + "placa.trono.4", "npc-odetes", "SIGN", "throne sign, line 4 (line 24)", "— Management", "— A Gerência"),
    # The Field Odete's clone and dialogs (npc-odete-de-campo)
    (F + "npc.name", "npc-odete-de-campo", "DIALOG", "Field Odete's name tag (fresh)", "Odete", "Odete"),
    (F + "npc.title", "npc-odete-de-campo", "DIALOG", "Field Odete's title (fresh)", "Field Registry", "Cartório de Campo"),
    (F + "dialog.title", "npc-odete-de-campo", "DIALOG", "Field Odete dialog title (fresh)", "Field Registry", "Cartório de Campo"),
    (F + "saudacao.manha", "npc-odete-de-campo", "DIALOG", "dialog 720 text, morning greeting (line 2)",
     "Good morning. This fall is not in the system. Neither is the applicant. Let's fix both.",
     "Bom dia. Esta queda não consta no sistema. O(a) requerente também não. Vamos regularizar as duas."),
    (F + "saudacao.tarde", "npc-odete-de-campo", "DIALOG", "dialog 721 text, afternoon greeting (line 3)",
     "Good afternoon. This fall is not in the system. Neither is the applicant. Let's fix both.",
     "Boa tarde. Esta queda não consta no sistema. O(a) requerente também não. Vamos regularizar as duas."),
    (F + "saudacao.noite", "npc-odete-de-campo", "DIALOG", "dialog 722 text, evening greeting (line 4)",
     "Good evening. This fall is not in the system. Neither is the applicant. Let's fix both.",
     "Boa noite. Esta queda não consta no sistema. O(a) requerente também não. Vamos regularizar as duas."),
    (F + "saudacao.veterano", "npc-odete-de-campo", "DIALOG", "dialog 723 text, a veteran's Back-Dated Fall (line 5)",
     "Old applicant, new file. The fall ran late too: seventy-seven years.",
     "Requerente antigo(a), processo novo. A queda também atrasou: setenta e sete anos."),
    (F + "opcao.moedas", "npc-odete-de-campo", "OPTION", "Field Odete option (line 9)", "Can I keep the coins?",
     "Posso ficar com as moedas?"),
    (F + "moedas", "npc-odete-de-campo", "CHAT", "answer to Can I keep the coins? (line 9)",
     "Finders keepers. Like your little egg: officially, I didn't see.",
     "Achado não é roubado. Que nem aquele seu ovo: oficialmente, eu não vi."),
    # The field Correction and the twin guard (item-ledger-scrap lines 9-13)
    (C + "campo.repetido", "item-ledger-scrap", "CHAT", "chat, a hat already owned (line 9)",
     "You had this one already. Now you have two.", "Esse você já tinha. Agora tem dois."),
    (C + "campo.sucesso", "item-ledger-scrap", "CHAT", "actionbar, every field Correction (line 10)",
     "Returned to the Royal Wardrobe. The Wardrobe, as it happens, is you.",
     "Devolvido ao Guarda-Roupa Real. O Guarda-Roupa, no caso, é você."),
    (C + "campo.jogador", "item-ledger-scrap", "CHAT", "actionbar, a scrap used on a player (line 11)",
     "People's hats are asked for, not corrected.", "Chapéu de gente se pede, não se averba."),
    (C + "campo.com_dono", "item-ledger-scrap", "CHAT", "actionbar, another player's animal (line 12)",
     "An owned animal: the hat goes home with it.", "Esse aí tem dono: o chapéu vai pra casa com ele."),
    (C + "campo.gemeo", "item-ledger-scrap", "CHAT", "actionbar, the twin guard (line 13)",
     "That little egg has an owner.", "Esse ovinho tem dono."),
    # The Table's JEI pages (system-corrections lines 3-11)
    ("orespawn_integrations.correction.jei.row_1", "system-corrections", "JEI", "JEI page on the HBM uranium ingot (line 3)",
     "Another world's uranium, entered as the King's. The weight a furnace gives. The crusher gives more.",
     "Urânio de outro mundo, averbado como do Rei. Mesmo peso que a fornalha dá. O triturador dá mais."),
    ("orespawn_integrations.correction.jei.row_2", "system-corrections", "JEI", "JEI page on BOP Spanish moss (line 4)",
     "Old man's beard is the Hoard's, sweetie. With Treasury paper it's auntie's moss.",
     "Barba-de-velho é do Tesouro, meu bem. Com papel da Tesouraria vira musgo da titia."),
    ("orespawn_integrations.correction.jei.row_3", "system-corrections", "JEI", "JEI page on Naturalist cooked crab (line 5)",
     "The Hoard eats the Land's crab and calls it the King's. Sign here.",
     "O Tesouro come caranguejo da Terra e chama de siri. Assina aí."),
    ("orespawn_integrations.correction.jei.row_4", "system-corrections", "JEI", "JEI page on the 18 emerald pieces (line 6)",
     "Double entry: two swords on one form. The Land allows a swap; we reverse the other.",
     "Lançamento em duplicidade: duas espadas no mesmo papel. A Terra deixa trocar; a gente estorna a outra."),
    ("orespawn_integrations.correction.jei.row_4_novo", "system-corrections", "JEI", "JEI page, row 4's second line (line 7)",
     "It comes out new: enchantments don't come along.", "A peça sai nova: encantamento não vem junto."),
    ("orespawn_integrations.correction.jei.row_7", "system-corrections", "JEI", "JEI page on the HBM titanium ingot (line 9)",
     "Another world's titanium, entered as the King's. In nuggets: the King signs for no bars.",
     "Titânio de outro mundo, averbado como do Rei. Em pepita, que barra o Rei não assina."),
    ("orespawn_integrations.correction.jei.r1", "system-corrections", "JEI", "JEI refusal page on both rubies (line 10)",
     "The Old Ground's ruby is the Land's and the court's is the court's. No correction for that.",
     "Rubi do Chão Velho é da Terra, e o da corte é da corte. Emenda, não tem."),
    ("orespawn_integrations.correction.jei.r2", "system-corrections", "JEI", "JEI refusal page on Marvel titanium (line 11)",
     "Hero titanium belongs to World No. 53. No correction for that.",
     "Titânio de herói é do Mundo nº 53. Emenda, não tem."),
    # Operator lines (roadmap 4.7)
    (R + "desligado", "npc-odetes", "CHAT", "operator, ops/registro_listar with the Registry off (fresh)",
     "The Registry is switched off in this server's config.", "O Cartório está desligado na configuração deste servidor."),
    (R + "cheio", "npc-odetes", "CHAT", "operator, the desk list is full; %s = 32 (fresh)",
     "The desk list is full: %s desks.", "A lista de balcões está cheia: %s balcões."),
    (R + "ja_listado", "npc-odetes", "CHAT", "operator, desk already listed; %s %s %s = its position (fresh)",
     "This desk is already listed (%s %s %s).", "Este balcão já consta na lista (%s %s %s)."),
    (R + "listado", "npc-odetes", "CHAT", "operator, desk listed; %s of %s (fresh)",
     "Desk listed: %s of %s.", "Balcão averbado: %s de %s."),
]

ADVANCEMENTS = [
    ("primeiro_retalho", "item-ledger-scrap", "First Scrap", "Pick up a piece of the torn Ledger.",
     "Primeiro Retalho", "Pegue um pedaço do Livro-Razão rasgado."),
    ("primeiro_gasto", "item-ledger-scrap", "Paid in Paper", "Spend a Ledger Scrap on anything.",
     "Pago em Papel", "Gaste um Retalho do Livro-Razão em qualquer coisa."),
    ("primeira_emenda", "item-ledger-scrap", "Returned to the Wardrobe", "Correct a stolen hat off a creature.",
     "De Volta ao Guarda-Roupa", "Faça a emenda de um chapéu roubado, direto da cabeça de uma criatura."),
]
for n, en, pt in ((1, "Sterling Uranium", "Urânio de Lei"), (2, "The Cuca's Moss", "O Musgo da Cuca"),
                  (3, "The King's Crab", "O Siri do Rei"), (4, "Double-Booked", "Dupla Entrada"),
                  (7, "Sterling Titanium", "Titânio de Lei")):
    ADVANCEMENTS.append(("row_%d" % n, "system-corrections", en, "A correction is in your book.", pt,
                         "Emenda lançada no seu livro."))
for path, card, en_t, en_d, pt_t, pt_d in ADVANCEMENTS:
    ROWS.append((A + path + ".title", card, "ADV", "advancement title", en_t, pt_t))
    ROWS.append((A + path + ".description", card, "ADV", "advancement description", en_d, pt_d))

LIMITS = {"SIGN": (15, 15), "HANGING": (10, 10), "CHAT": (90, 103)}


def main():
    out = os.path.join("_lang_fragments", "0.10.0")
    keys, en, pt = {}, {}, {}
    for key, card, surface, where, e, p in ROWS:
        assert key not in keys, key
        if surface in LIMITS:
            limit_en, limit_pt = LIMITS[surface]
            assert len(e) <= limit_en, (key, e)
            assert len(p) <= limit_pt, (key, p)
        if surface == "OPTION":
            assert len(e.split()) <= 6 and len(p.split()) <= 6, key
        keys[key] = "%s | %s | %s | %s" % (card, surface, where, e[:40])
        en[key] = e
        pt[key] = p
    for name, data in (("registry.keys.json", keys), ("registry.en_us.json", en), ("registry.pt_br.json", pt)):
        with open(os.path.join(out, name), "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print(len(keys), "keys")


if __name__ == "__main__":
    main()
