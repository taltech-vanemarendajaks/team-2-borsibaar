# Börsibaar – testiplaan (lihtne)

**Projekt**: Börsibaar (Spring Boot + Next.js + PostgreSQL)  
**Kuupäev**: 01.02.2026  
**Versioon**: 1.0

## 1. Testimise eesmärgid

- Kontrollida, et põhilised kasutusvood töötavad (sisselogimine, inventar, müük/POS, avalik hinnavaade).
- Tagada, et andmed on organisatsioonide vahel eraldatud (multi-tenant): kasutaja näeb/haldab ainult oma organisatsiooni andmeid.
- Vähendada regressioonide riski: kriitilised ärireeglid kaetakse automaattestidega.
- Leida vead enne väljastust (valed hinnad, negatiivsed kogused, valed õigused, katkised API-d).

## 2. Testimise tasemed

### 2.1 Ühiktestid (Unit)
- Backendis: teenused, utiliidid, mapperid, ärireeglite väiksemad osad.
- Eesmärk: kiire tagasiside, loogika kontroll eraldatult.

### 2.2 Integratsioonitestid (Integration)
- Backendis: REST kontrollerid + teenused + (test)andmebaas.
- Eesmärk: kontrollida päringuid, validatsiooni, turvet ja andmevoogu kihtide vahel.

### 2.3 Süsteemitestid (System / E2E – lihtne)
- Manuaalsed testid läbi UI (Next.js) + backend + andmebaas.
- Eesmärk: kasutaja vaates „kõik töötab koos“.

## 3. Testimise ulatus (Scope)

### 3.1 Testitav (In scope)
- **Autentimine ja autoriseerimine**
  - Google OAuth2/JWT voog (nii palju kui keskkond lubab), sessiooni kontroll, ligipääsupiirangud.
- **Organisatsioon ja kasutajad**
  - Organisatsiooni valik/kuuluvus, rollid (USER/ADMIN) ja õiguste kontroll.
- **Tooted ja kategooriad**
  - Loomine/muutmine/kustutamine (kui toetatud), hinnapiirid (min/max/base), nime unikaalsus organisatsioonis.
- **Inventar**
  - Laoseisu muutmine (sisse/ välja/ korrigeerimine), kogus ei lähe negatiivseks, tehinguajaloo loomine.
- **POS / müük**
  - Müügi lisamine, laoseisu vähenemine, hinnaloogika (sh võimalik dünaamiline hind).
- **Avalik hinnavaade**
  - Hinnad kuvatakse õigesti ja loogilises vormis.
- **API veateated**
  - Mõistlikud staatuskoodid (401/403/400/404/500) ja järjepidev käitumine.

### 3.2 Mittetestitav / piiratud (Out of scope või piiratud ajaga)
- Suured koormustestid (täismahus perf testid) – ainult lihtsad „smoke“ kontrollid.
- Täielik turvaaudit – ainult põhilised kontrollid (õigused, tenant isolation).
- Kõigi brauserite 100% tugi – keskendume levinumatele.

## 4. Testimise lähenemine (Approach)

- **Riskipõhine**: kõigepealt kriitilised vood (login, inventar, müük, tenant isolation).
- **Automatiseerimine**
  - Backend: JUnit/Spring Boot testid (ühik + integratsioon).
  - Frontend: kuna projektis puudub hetkel testiraamistik (nt Jest/Playwright), tehakse UI E2E alguses manuaalselt.
- **Manuaalne testimine**
  - UI „happy path“ + peamised veajuhud (negatiivsed kogused, valed hinnad, õiguste puudumine).
- **Testandmed**
  - Kasutada seed-andmeid (Liquibase) või test-organisatsiooni ja paari testkasutajat.

## 5. Testikeskkond (Environment)

### 5.1 Kohalik arendus (Local)
- **Backend**: Spring Boot (Docker Compose), Java 21.
- **DB**: PostgreSQL (Docker).
- **Frontend**: Next.js (Node.js), käivitus `npm run dev`.
- **Brauserid**: Chrome (põhiline), Firefox (lisakontroll).

### 5.2 Testandmebaas
- Integratsioonitestides võib kasutada eraldi test DB-d (nt H2 või eraldi PostgreSQL konteiner), et vältida pärisandmete mõjutamist.

### 5.3 Konfiguratsioon
- `.env` väärtused olemas (DB, OAuth2, JWT).
- CORS/URL-id õiged (localhost:3000 ↔ backend).

## 6. Sisenemise ja väljumise kriteeriumid

### 6.1 Entry criteria (alustamise tingimused)
- Arendusbuild töötab: backend + DB käivituvad; frontend käivitub.
- Põhifunktsionaalsused on implementeeritud (vähemalt MVP tasemel).
- Testandmed ja vähemalt 1 testkasutaja on olemas.

### 6.2 Exit criteria (lõpetamise tingimused)
- Kriitilised vood läbitud ilma „blocker“ vigadeta.
- Kõrge prioriteediga vead (P1) parandatud või teadlikult aktsepteeritud.
- Vähemalt:
  - backendis põhiteenustel/kontrolleritel olemas minimaalsed testid,
  - süsteemitestis tehtud „smoke“ kontroll (login + inventar + üks müük + avalik vaade).
- Koostatud lühike testiraport (mis testiti ja mis jäi tegemata).

## 7. Rollid ja vastutused

- **Arendaja(d)**
  - Kirjutab ühik- ja integratsioonitestid.
  - Parandab leitud vead.
- **Testija (võib olla sama inimene)**
  - Teeb manuaalsed UI/süsteemitestid.
  - Dokumenteerib testjuhtumid ja vead.
- **Toote/tiimi esindaja**
  - Kinnitavad, et tulemused vastavad ootustele (lihtne acceptance).

## 8. Riskid ja eeldused

### 8.1 Riskid
- OAuth2 (Google) sõltub välisest teenusest; lokaalne testimine võib olla piiratud.
- Tenant isolation vead võivad olla „peidetud“ ja ilmnevad hiljem, kui on mitu organisatsiooni.
- Dünaamilise hinna loogika võib põhjustada ootamatuid edge-case’e.
- Aja puudus: UI automaattestid võivad jääda tegemata.

### 8.2 Eeldused
- Testimiseks on olemas vähemalt 2 organisatsiooni ja 2 kasutajat (et isolatsiooni kontrollida).
- Keskkonnamuutujad on korrektselt seadistatud.

## 9. Testitavad stsenaariumid (näited)

- **Login**: sisselogimine õnnestub; kaitstud lehele ilma loginita ei saa.
- **Õigused**: USER ei saa ADMIN tegevusi (kui rollid on eristatud).
- **Tenant isolation**: kasutaja A ei näe organisatsiooni B tooteid/inventari.
- **Inventar**: laoseisu vähendamisel ei teki negatiivset kogust; tekib tehing.
- **POS**: müük vähendab laoseisu ja salvestab tehingu.
- **Avalik leht**: hinnad kuvatakse; valed/puuduvad andmed ei „crashi“ lehte.

## 10. Testimise deliverable’id (tulemused)

- See testiplaan.
- Testjuhtumite loetelu (võib olla lihtne tabel või checklist).
- Testide tulemus (JUnit raport / CI logid, kui kasutatakse).
- Vigade nimekiri (issue’d või lihtne dokument).
- Lõpp-testiraport (mis testiti, mis jäi riskina üles, soovitused).
