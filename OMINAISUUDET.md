# PvPCalc 3.0.0 - ominaisuuslista ja tila

✔ = valmis, ◐ = osittain, ✘ = ei mukana (syy kerrottu)

## Pyynnöt
- ✔ Varusteet luetaan automaattisesti inventorysta (ase, panssari, loitsut, efektit) ja käytetään vahinkolaskennassa: `/pvpcalc gear`, `/pvpcalc calc`
- ✔ Versio 1.8 / 1.9+: `/pvpcalc version`
- ✘ Tuki 1.21.11 – 26.3+ samalla jarilla: ei onnistu. Minecraft 26.x käyttää eri sisäisiä nimiä, joten tarvitaan oma käännös (versio 4)
- ✔ Kohde: katsottu pelaaja tai itse: `/pvpcalc target`
- ✔ Kilpi-oletus: `/pvpcalc shield`
- ✔ Vastustajan HP armor-baarin yläpuolella (päälle/pois): `/pvpcalc hphud`
- ✔ Vaikeustaso Easy/Normal/Hard (vaikuttaa räjähdyksiin; pelaajien välinen lähitaistelu ei skaalaudu vanillassa)
- ✔ Kaksoisvalinta `pvp`-komennosta korjattu (vain yksi `pvp`; isot/pienet kirjaimet toimivat silti)
- ✔ Asetusvalikko: `/pvpcalc settings` ja näppäin O
- ✔ Pikanäppäimet (Controls-valikossa): asetukset, päälle/pois, HUD, huijausarvio, uusi ottelu, seuraa katsottua, viime yhteenveto, HUD:n nollaus
- ✔ Pyöristys: desimaalit ja HP/sydämet/molemmat (oletus 1 desimaali)
- ✔ `/pvpcalc stats` + iso yhteenvetoruutu `/pvpcalc matchover`
- ✔ Crit-% (CSV: matches.csv, match_players.csv)
- ✔ Vastustajan panssarin kestävyys HUD:ssa (kulma valittavissa, päälle/pois), heikoin osa merkitty (XP-pullon kohde)
- ✔ Gap-, XP-pullo- ja pearl-laskurit + CSV-sarakkeet + yhteenvetoruutu (gapit: arvio käyttöajan perusteella)
- ✔ Kuolinanalyysi (viimeinen vahinko, vastustajan HP:n jäännös / ylivahinko, tappajan nimi ja ase). Vaatii vanilla-kuolinviestin
- ✔ Nuijan tarvittava putoamiskorkeus (loitsut, panssari, Resistance huomioitu)
- ✔ Excelissä näkyy kehen vahinko tehtiin / kuka popautti (Player-sarake)
- ✔ Voittoputki ja voitot/häviöt kitkohtaisesti, jokaista pelaajaa vastaan omat rivit; EI nollaudu `/pvpcalc reset`:llä
- ✔ Äänimerkit totem-popista ja tappovahingosta; "X popped!" ruudulle
- ✔ Kit-suositus: `/pvpcalc recommend <pelaaja>`; EI nollaudu resetillä
- ✔ Ennätykset (suurin osuma, pisin kombo) kitkohtaisesti ja pelaajakohtaisesti (records.csv); EI nollaudu resetillä
- ✔ Pelaajan auto-seuranta lyödessä, oma asetusosio ja pikanäppäin
- ✔ Toistuvat epäillyt (hack_suspects.csv), luottamustaso (vähän/paljon dataa + ottelumäärä)
- ✔ Kategoriakohtainen hälytys (oma raja, teksti ruudun keskellä, ääni, oletus sonic boom)
- ✔ Lag-rivi (HACK LAG), palvelimen TPS-hyvitys, pingin hyvitys
- ◐ Kit-kohtaiset rajat: mace, elytra, keihään lunge huomioitu. Attribute swap ja ghost block eivät näy clientille, joten niitä ei tunnisteta
- ✔ Tunnetut väärät hälytykset (elytra, wind charge, ilotulite, pearl, räjähdys, ajoneuvo, vesi, ym.) listana asetuksissa
- ✔ Voittoennuste, resurssilaskuri (kits.json, muokattavissa), SMP-tila, reaktioaika, etäisyys/spacing, pelityyli, tavoitteet, kehitystrendi, parannusvihjeet
- ◐ SMP-tila: näyttää vain käytetyn määrän; varusteet luetaan suoraan, joten arvausta ei tarvita
- ✔ Yhteenveto chattiin lyhyenä + avaus isoksi; per pelaaja avattava osio ja hack-%
- ✔ Pelaajan oma perustaso (CPS), ohje (`help`, myös ensikäynnistyksessä), hiljainen tila, config export/import, suorituskykytila
- ◐ Musiikki: `/pvpcalc music play` soittaa Minecraftin levyt ja resource packin ääniä. Azalin musiikkia ei voi sisällyttää (tekijänoikeus; kerro mitä tarkoitat)
- ✔ UUID-seuranta, NPC-/botti-/spectator-/mobisuodatus
- ✔ Simulaatio (`hack simulate`), palvelinasetukset tunnetuille palvelimille (päälle/pois), aliakset `/pvp` ja `/hack`
- ✔ Päivitysilmoitus (vain ilmoitus; toimii kun GitHubiin on tehty julkaisu)
- ✔ Komentoehdotukset, selkeät virheilmoitukset, viestien toistorajoitus, `/pvpcalc check`, hover-tekstit, tab-täydennys, `config reset` vahvistusklikillä, oletusarvot näkyvissä
- ✔ Eheä sulkeutuminen (CSV-puskuri kirjoitetaan), ensikäynnistyksen tarkistus, ei turhia kirjoituksia
- ✔ README + esimerkki-CSV:t englanniksi (O_Kissa mainittu)
- ✔ Virheensuojaus: try/catch tarkistuskohtaisesti, 5 virheen jälkeen pois päältä, turvatila, globaali tick-suoja, NaN/nollajako, %-rajaus 0-100, dimensio/kuolema/respawn-armonaika, palvelimenvaihto (HACK SUMMARY ensin), yksinpeli/LAN, kadonneet pelaajat, tuntematon ping, turvallinen HUD
- ✔ Kielivalinta fi/en (oletus en)

## Asetusvalikko (Clauden ohjeen mukaan)
- ✔ Välilehdet, haku, lista, ON/OFF-/kierrätysnapit, tekstikentät (punainen jos arvo virheellinen), nollausnappi per asetus, korostus muutetuille, Reset tab (varmistus), Export, Import, Done, vihjetekstit
- ✔ Pikanäppäimet: vaihdettavissa asetusruudun Hotkeys-välilehdeltä (klikkaa, paina uusi näppäin, Esc tyhjentää, punainen = ristiriita) sekä Minecraftin Controls-valikosta
- ✘ Mod Menu -liitäntä (vaatii erillisen käännösriippuvuuden), HUD:n skaalaus ja vedettävä esikatselu, värivalitsin ja listaeditori (tilalla tekstikentät)
- ◐ Oletusnäppäin on O eikä P, koska P on Minecraftin Social Interactions -näppäin

## Huijausarvion tarkistukset
- ✔ Reach, Speed, Fly, Aimbot (snap), Autoclicker
- ✔ (kokeellisia, oletuksena pois) Velocity, NoFall, AutoTotem, Mace
- ✘ Scaffold ja Criticals: clientille ei näy riittävästi tietoa luotettavaan tunnistukseen
