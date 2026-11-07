# OreSpawn Integrations 0.10.1: The First Night and Week

The Land now keeps a calendar. After the first hour comes the first night, and after it a week with a name for every
day: a newspaper at dawn, bounties on Monday, a peddler on Wednesday, stories at the bar on Saturday, and something in
the woods on Sunday if you hunted too much. This is an alpha: everything here is playable and tested, and the story goes
on in the next versions.

## What's new
- **The Land's Week.** The Land keeps its own day count, one more at every dawn (a `/time set` is one more day, never a
  rewind), and the sunrise names the weekday. In your first week each new thing is explained one a day, not all at once.
- **The Almanac.** One event a night per dimension, two on a server. Thursday and Friday nights stay quiet unless the
  Fall or the Mapinguari takes the slot, and a restart never re-rolls the day.
- **The Hoard Fall.** At night a Fall can come down with a warning 30 seconds ahead, a gold streak and a whistle: a bowl
  of natural ground with uranium and titanium, three Aliens, and a vault that scales with your rank. It digs no holes in
  your base (`alive.meteor_grief` still turns block damage on).
- **The Mãe-da-lua.** One dusk line for each player each dusk, never right on top of a first-hour step. The urutau spawns
  at last in the Old Ground and the Greenwood, on open ground at dusk.
- **The Interregnum Gazette.** A newspaper in your mailbox every dawn, in your own language: yesterday's biggest deed,
  Today's Misfile, the forecast, the classifieds, and on Sunday Lindalva's notice. New players get the Newcomer's Edition
  in their first week.
- **Today's Misfile.** Each day the canopy can change: Fish in the Trees, Raining Hats, a Tuesday with almost nothing, a
  Sunday with double scraps. Breaking leaves can now also drop a misfiled OreSpawn creature, under Slow Falling and
  carrying its Misfile Tag, or a stray egg.
- **The Deed Log.** Your deeds are kept and told one size bigger each Saturday. Operators can read them with `/cc deeds`.
- **The Hunt Lodge and the Wall of the Wanted.** One Lodge per new world, 300 to 600 blocks out, with Captain Iracema, the
  Wall's seven Wanted posters and a board of three bounties every Monday, one paid in Reais at triple pay. On an older
  world the board opens at any Monster Hunter tent (sneak-use its workbench) and at Registry desks. An operator can place
  the Lodge with `/cc hunt_lodge place`.
- **Seu Zé's boteco.** A bar you can find in the Old Ground, the plains, the jungle and the savanna, in place of Brasil e
  Coisas' unfindable one (`story.boteco` off brings that one back). On Saturday at dusk, everyone at his counter hears up
  to four of their own deeds retold.
- **Wednesday's peddler.** A wandering trader and two llamas come to a newcomer's bed in their first week, or else to the
  week's best-kept house. He leaves after 40 minutes, later if someone is trading.
- **The Mapinguari's Sunday.** Hunt the Land's animals away from home on a Sunday and you get a warning at the second
  kill. The third brings the Mapinguari at dusk. Three raw meats send him back to the woods, and he is gone by dawn. Three
  clean Sundays leave his fang at your bed.
- **The Parish Stone and the Plot Stake.** A stone counts the Manor decor you build near it, through four stages, with
  arrivals at dawn, plots to sign and a bell for the Town. Inside a yard, a casket's traps become a small gift from the
  Land.
- **Court ranks.** Your rank is the same everywhere and a rank-up grants every card you skipped in one ceremony. Squire
  also comes by visiting two provinces on two days. Players from before this version are backfilled at their next login.
- **The Mestre.** His gift and blessing stay as they are, and now one line of ours follows them. He no longer spawns in
  the Twilight Forest or the Bumblezone.
- **Jade.** Wanted posters, a Hunter's Workbench, the Parish Stone and the Land's animals all say what they are.
- **Quests 701 to 706** read properly in both languages. Nobody's quest progress changes, and an operator's own edit of
  them is kept.

## Fixes
- **Shelf 9 turns up on many more new worlds.** Its land search misjudged ground in places the world hadn't generated
  yet, and it gave up 48 blocks from spawn. It now reads that ground properly and keeps looking out to 192 blocks, for up
  to a minute while the world is created. You still wake on the straw wherever it lands, and it no longer settles on a
  sky-ship's deck. In our tests on 24 worlds, the old search found no site on 6, and the new one placed all 24.
  pt-BR: **A Prateleira 9 aparece em muito mais mundos novos.** A busca por terra errava a leitura do chão nos lugares que o
  mundo ainda não tinha gerado, e desistia a 48 blocos do spawn. Agora ela lê esse chão direito e continua procurando até
  192 blocos, por até um minuto durante a criação do mundo. Você continua acordando na palha onde quer que ela fique, e
  ela não pousa mais no convés de um navio voador. Nos nossos testes com 24 mundos, a busca antiga não achou lugar em 6, e a nova colocou nos 24.
- Running the OreSpawn Integrations jar without the pack: the same read fix applies, since that ground reading depended on
  another mod in the pack. Use 0.10.1 or newer.
  pt-BR: Para quem usa o jar do OreSpawn Integrations sem o pack: a mesma correção vale, porque aquela leitura do chão
  dependia de outro mod do pack. Use a 0.10.1 ou mais nova.
- The Registry pin on a far village no longer sits at the bottom of the world on the map, and a landing's coins and Aliens no
  longer can when their player has walked away.
  pt-BR: O marcador do Registro numa vila distante não fica mais no fundo do mundo no mapa, e as moedas e os Alienígenas de um
  pouso também não quando o jogador já foi embora.
- Baron von Bats joins the other Plants & Zombies bosses on the heavyweights list and the spawner blacklist.
  pt-BR: O Barão Von Morcego se junta aos outros chefes de Plants & Zombies na lista de pesos-pesados e na lista proibida de geradores.
- **A new world always looks for its Hunt Lodge.** When the world's own placement finds nothing, usually because it landed out
  at sea, the first start puts the Lodge on good ground 300 to 600 blocks from spawn, the card's regions first. In our tests
  on 8 new worlds, all 8 got a Lodge. A world with no land in range still gets the Monday board at Monster Hunter tents and
  Registry desks.
  pt-BR: **Todo mundo novo procura o seu Pavilhão de Caça.** Quando o posicionamento do próprio mundo não acha nada, quase
  sempre por cair no mar, a primeira abertura põe o Pavilhão em chão bom a 300–600 blocos do spawn, nas regiões do card
  primeiro. Nos nossos testes com 8 mundos novos, os 8 ganharam um Pavilhão. Um mundo sem terra ao alcance continua com o
  quadro de segunda-feira nas tendas de Caçador e nos Balcões do Registro.
- Captain Iracema is no longer lost when her Lodge appears before she can: she waits in the world's data until she can stand
  there, through a restart too.
  pt-BR: A Capitã Iracema não se perde mais quando o Pavilhão aparece antes dela: ela espera nos dados do mundo até poder
  ficar lá, inclusive depois de reiniciar.
- **A Royal Casket's trap no longer burns or breaks a roof.** Since alpha.1, a trap's fireball could set a roof above the
  player alight and its TNT could blow holes in a roof and floor, because other mods in the pack change how explosions
  work. Its blasts now hurt only the players in range, with no fire and no broken block.
  pt-BR: **A armadilha de uma Arca Real não queima nem quebra mais um telhado.** Desde a alpha.1, a bola de fogo de uma
  armadilha podia pôr fogo no telhado acima do jogador e o TNT podia abrir buracos no telhado e no chão, porque outros mods
  do pack mudam como as explosões funcionam. Agora as explosões dela só ferem os jogadores por perto, sem fogo e sem
  quebrar bloco.
- The game no longer crashes in the creative inventory with Refurbished Furniture ("Cannot assign field visible because
  this.scrollUpButton is null"): its filter tabs wait until their buttons exist.
- Long region names and subtitles no longer run off the screen on small GUI scales: a title or subtitle that wouldn't fit
  shrinks just enough. This covers any other mod's titles too.
- A Back-Dated Fall that can't find a landing spot now logs why, the same way a newcomer's does, instead of a line of
  zeros.

## Fixes found while testing
- A Hoard Fall's vault stayed empty if its player had gone offline before the landing.
- A boss kill paid its Wall row and bounty marks only to the first four players named in the deed line, not everyone
  within 32 blocks as the Wall promises.
- A world could not load its registries because the Hunt Lodge's structure set had no salt.

## Known issues
- Creating a first world can take up to about a minute on a handheld, while Shelf 9 looks for a place to stand.

## Details
- Minecraft 1.21.1, NeoForge 21.1.252. Built for the CrazyCraft 5.0 pack (v2.0.0-alpha.4).
- 142 GameTests run for every build, on a test server and in the full pack, and the first night and week above were
  checked on a real client in the full pack.
- The Four Ways signs are surveyed once more at the first start on this version, so the bar's way can name the boteco.
