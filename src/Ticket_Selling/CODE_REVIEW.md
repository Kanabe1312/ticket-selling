# Code review — Ticket Selling

Capstone 2 din `aplicatie-finala/2-vanzare-bilete`. Soluția trăiește în
`src/Ticket_Selling/`.

Structura pe clase e bună (`Customer`, `Ticket`, `TicketStore`,
`Statistics`, `PurchaseTask`, `LiveDisplayTask`, `Main`), `buyTicket()` e
sincronizat corect, `LiveDisplayTask` are condiție de ieșire curată plus
daemon. **Se compilează, rulează, trece R1–R7 funcțional.**

---

## Verificare la rulare

```
$ javac Ticket_Selling/*.java && time java Ticket_Selling.Main
=== Sumar ===
Sold: 50
Rejected: 30
First 5 customers:
  Client-001 -> Ticket #5
  Client-002 -> Ticket #7
  Client-003 -> Ticket #8
  Client-004 -> Ticket #11
  Client-005 -> Ticket #9
Last 5 customers:
  Client-046 -> Ticket #42
  Client-047 -> Ticket #47
  Client-049 -> Ticket #48
  Client-050 -> Ticket #49
  Client-055 -> Ticket #50
Duration: 979 ms
```

- 50 + 30 = 80 (toți clienții procesați) ✅
- ID-ul maxim observat = 50 (= stocul inițial) ✅
- Programul revine în shell, fără hang ✅

---

## Cerințe vs. soluție

| R  | Cerință                          | Status | Notă |
|----|----------------------------------|--------|------|
| R1 | Pool de 10, paralel              | ✅     | 979 ms ≪ 12 s; `Executors.newFixedThreadPool(10)` corect |
| R2 | Exact 50 vândute                 | ✅     | `buyTicket()` synchronized verifică `ticketsLeft <= 0` |
| R3 | ID-uri unice 1..50               | ✅     | `nextTicketId++` sub același lock cu `ticketsLeft--` |
| R4 | 30 respinși cu FAIL              | ✅     | `Sold: 50` + `Rejected: 30` |
| R5 | Afișor live                      | ⚠️     | apare la fiecare ~50 ms, dar **mesajul nu conține `[live] bilete ramase: X`** cum cere README |
| R6 | Sumar final                      | ⚠️     | are sold, rejected, first 5, last 5, duration — ✅, dar vezi #2 mai jos pentru semantica „primii/ultimii" |
| R7 | Ieșire curată                    | ✅     | `LiveDisplayTask` iese pe `processed < 80` + e daemon |

Singurul R care **strict** pică verificarea din README e R5 (format mesaj).

---

## Ce e bine

- `TicketStore.buyTicket()` e `synchronized` și combină verificarea + scăderea
  + alocarea ID-ului într-o singură secțiune critică — exact ce trebuie ca să
  nu vinzi două bilete cu același ID.
- `TicketStore.getTicketsLeft()` e `synchronized` — afișorul live citește
  consistent (corectat față de ATM, unde `getBalance` era nesincronizat).
- `LiveDisplayTask` are **două** mecanisme de oprire: condiția
  `processed < 80` și daemon flag. Asta e robust — chiar dacă uitai daemon,
  oricum se oprea.
- `PurchaseTask` întoarce `Ticket` (sau `null` pentru fail). Main poate
  separa OK / FAIL direct din `future.get()` fără să interogheze altă stare.
- Sleep-ul random `random.nextInt(101) + 50` produce [50, 150] ms — fix
  intervalul cerut de README.
- `Duration` măsurată cu `System.currentTimeMillis()` — corect pentru R6.

---

## Probleme

### 1. Mesajul live nu respectă formatul din README

```java
// LiveDisplayTask.java:16-20
System.out.println();
System.out.println("Live Display ");
System.out.println("No. of left tikets : " + ticketStore.getTicketsLeft());
System.out.println("Processed requests: " + statistics.getProcessed());
```

README zice literal:

> apar cel putin 2 linii "[live] bilete ramase: X" cu valori diferite

Pe lângă format, e și un typo: `tikets` → `tickets`.

**Fix:**

```java
System.out.println("[live] bilete ramase: " + ticketStore.getTicketsLeft()
        + " (processed: " + statistics.getProcessed() + ")");
```

(Sau orice format care conține literal `[live] bilete ramase: X`.)

### 2. „Primii 5 / Ultimii 5" — ordinea e după index, nu după timp

```java
// Main.java:48-61
List<String> succesfulCustomers = new ArrayList<>();
for (int i = 0; i < futures.size(); i++) {
    Ticket ticket = futures.get(i).get();
    if (ticket != null) {
        sold++;
        succesfulCustomers.add(customers.get(i).getName() + "->Ticket #" + ticket.getId());
    }
}
```

Adaugi în `succesfulCustomers` în **ordinea index-ului customer-ului**
(Client-001, Client-002, ... Client-080), nu în ordinea cronologică în care
au reușit cumpărarea. Asta se vede în output: `Client-001 -> Ticket #5`
(Client-001 a primit ticket al 5-lea, nu primul).

README spune „primii 5 clienti care au primit bilet" — interpretarea
naturală e **cronologic**: cei care au prins ticket #1, #2, #3, #4, #5.

**Fix simplu** — sortează după ID-ul ticket-ului înainte de slice:

```java
// stochează (nume, ticketId) ca pereche în loc de string;
// la final, sortează după ticketId.
record SoldEntry(String name, int ticketId) {}
List<SoldEntry> sold = new ArrayList<>();
// ... în loop:
sold.add(new SoldEntry(customers.get(i).getName(), ticket.getId()));
// ... la sumar:
sold.sort(Comparator.comparingInt(SoldEntry::ticketId));
// primii 5 = sold.subList(0, 5)
// ultimii 5 = sold.subList(sold.size() - 5, sold.size())
```

Acceptabil și fără sortare dacă spui în mesaj „primii 5 clienți (după
listă)" în loc de „primii 5 clienți". Dar interpretarea cronologică e mai
naturală.

### 3. `succesfulCustomers.size() - 5` — asumare ascunsă

```java
for (int i = succesfulCustomers.size() - 5; i < succesfulCustomers.size(); i++) {
    System.out.println(succesfulCustomers.get(i));
}
```

Dacă `succesfulCustomers.size() < 5`, expresia devine negativă →
`IndexOutOfBoundsException`. În scenariul curent nu se întâmplă (50 > 5),
dar dacă cineva schimbă stock-ul la 3 bilete, crashează.

**Fix:**

```java
int start = Math.max(0, succesfulCustomers.size() - 5);
for (int i = start; i < succesfulCustomers.size(); i++) {
    System.out.println(succesfulCustomers.get(i));
}
```

### 4. `new Random()` în fiecare `PurchaseTask.call()`

```java
// PurchaseTask.java:18-20
Random random = new Random();
Thread.sleep(random.nextInt(101) + 50);
```

Creezi 80 instanțe de `Random`. Funcțional ok, dar:
- `Random` e thread-safe printr-un CAS pe `seed` → contention pe pool
  multi-thread (chiar dacă fiecare are propria instanță, e wasteful);
- `ThreadLocalRandom.current()` e exact instrumentul potrivit pentru un
  task într-un thread pool.

**Fix:**

```java
Thread.sleep(ThreadLocalRandom.current().nextInt(50, 151));
```

(`nextInt(50, 151)` = inclusiv 50, exclusiv 151 → [50, 150], identic
semantic.)

### 5. Print debug / mesaje neclare

```java
// LiveDisplayTask.java:17
System.out.println("Live Display ");  // spațiu la final, fără paranteze
```

```java
// Main.java:89
System.out.println();System.out.println("Duration: " + (endTime - startTime) + " ms");
```

Două statement-uri pe o linie. Despărțite, citirea e mai ușoară. Minor.

---

## Observații pe `Main.java`

### 6. Timing — `endTime` luat înainte de print

```java
// Main.java:63-69
long endTime = System.currentTimeMillis();
executor.shutdown();
System.out.println();
System.out.println("=== Sumar ===");
...
```

Asta e ok — `Duration` reflectă **procesarea**, nu **printarea sumarului**.
Bine. Dar dacă vrei să incluzi și timpul de printare, mută `endTime` la
final.

### 7. `throws Exception` pe `main`

Idem ca la ATM. Pentru lecție e ok; pe viitor `try/catch` pe
`InterruptedException` și `ExecutionException` e mai expresiv.

---

## Mici stilistice (opțional)

- `package Ticket_Selling` — convenția Java cere lowercase
  (`ticket_selling` sau `ticketselling`). La fel ca la ATM.
- Câmpurile din `Customer`, `Ticket`, `TicketStore.id`-ul (nu există dar
  exemplu) ar putea fi `final` — set-once în constructor.
- `succesfulCustomers` → `successfulCustomers` (typo).
- `tikets` în mesaj → `tickets`.
- `Customer` și `Ticket` n-au `toString()` — în loc de
  `customer.getName() + "->Ticket #" + ticket.getId()`, ai putea face
  `customer + " -> " + ticket` cu un `toString()` decent.

---

## TODO

Direcție generală: **scoate `synchronized` din cod, folosește `ReentrantLock`
pentru `TicketStore` și `AtomicInteger` pentru `Statistics`.** Cod complet
în anexa de jos.

Ordonat după impact:

1. ✏️ **Mesajul `[live] bilete ramase: X`** în `LiveDisplayTask.run()`
   (singurul blocker pentru R5 strict). Și fix typo `tikets`.
2. ✏️ **Refactor `TicketStore` la `ReentrantLock`** (vezi anexa) —
   `buyTicket` + `getTicketsLeft` sub același lock final, pattern
   `lock()/try/finally/unlock()`. Scoate cele 2 `synchronized` existente.
3. ✏️ **Refactor `Statistics` la `AtomicInteger`** — un singur câmp `int`
   cu increment + read e exact cazul de Atomic, nu lock manual.
4. ✏️ Sortează `successfulCustomers` după `ticketId` înainte de „first 5 /
   last 5" — interpretarea cronologică e cea naturală a cerinței.
5. ✏️ `Math.max(0, size - 5)` pentru slice-ul „last 5".
6. ✏️ `ThreadLocalRandom.current().nextInt(50, 151)` în loc de
   `new Random()` per task.

Opțional:

- Curăță layout-ul liniei 89 (`Duration`).
- Fix typo `succesfulCustomers` → `successfulCustomers`.
- Marchează câmpurile imutabile cu `final`.
- Renunță la `throws Exception` în favoarea `try/catch`.

---

## Verificare după fix-uri

Rulează de ~5–10 ori:

- toate rulările: `Sold: 50`, `Rejected: 30` (R2, R4),
- timpul total < 2 s (R1),
- apar ≥ 2 linii care **conțin** literal `[live] bilete ramase:` cu valori
  diferite (R5),
- în sumar, `first 5` are ticket-urile #1..#5 (după sortare) (R6),
- shell-ul revine fără să atârne (R7).

---

## Anexă — codul complet pentru refactor (TODO #2, #3)

Direcția preferată: **scoate complet `synchronized`, mergi pe Lock-uri
explicite + `AtomicInteger`**. Asta îți deschide drumul către `tryLock`,
timeout-uri și condition variables în lecția 03.

### Idee în 3 propoziții

- `synchronized` îți ia lock-ul **pe obiect** (`this` la metode de
  instanță), implicit, fără să-l vezi.
- `ReentrantLock` îl declari ca **field final** și îl iei / eliberezi
  explicit cu `lock.lock()` / `lock.unlock()`.
- Aceeași semantică (mutual exclusion + reentrant), dar control mai fin.

### Pattern obligatoriu: `try/finally`

```java
lock.lock();        // în afara try — nu vrei să unlock-uiești ce n-ai luat
try {
    // sectiunea critica
} finally {
    lock.unlock();  // mereu în finally — altfel, dacă crapă codul,
                    // lock-ul ramane prins si app-ul se blocheaza
}
```

### TicketStore cu `ReentrantLock`

```java
package Ticket_Selling;

import java.util.concurrent.locks.ReentrantLock;

public class TicketStore {
    private int ticketsLeft;
    private int nextTicketId;
    private final ReentrantLock lock = new ReentrantLock();

    public TicketStore(int ticketsLeft) {
        this.ticketsLeft = ticketsLeft;
        this.nextTicketId = 1;
    }

    public int getTicketsLeft() {
        lock.lock();
        try {
            return ticketsLeft;
        } finally {
            lock.unlock();
        }
    }

    public Ticket buyTicket() {
        lock.lock();
        try {
            if (ticketsLeft <= 0) {
                return null;
            }
            Ticket ticket = new Ticket(nextTicketId);
            nextTicketId++;
            ticketsLeft--;
            return ticket;
        } finally {
            lock.unlock();
        }
    }
}
```

Observații:
- **Același** `lock` pentru `buyTicket` și `getTicketsLeft` — așa monitorul
  nu vede stări intermediare. Echivalent cu `synchronized` pe `this`.
- `final` pe `lock` — nimeni nu îl mai înlocuiește din greșeală cu altă
  instanță (asta ar sparge tot — fiecare thread ar lua alt lock).

### Statistics cu `ReentrantLock`

```java
package Ticket_Selling;

import java.util.concurrent.locks.ReentrantLock;

public class Statistics {
    private int processed;
    private final ReentrantLock lock = new ReentrantLock();

    public void incrementProcessed() {
        lock.lock();
        try {
            processed++;
        } finally {
            lock.unlock();
        }
    }

    public int getProcessed() {
        lock.lock();
        try {
            return processed;
        } finally {
            lock.unlock();
        }
    }
}
```

**Mai elegant aici:** un singur `int` cu increment + read e exact ce face
`AtomicInteger`:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class Statistics {
    private final AtomicInteger processed = new AtomicInteger();
    public void incrementProcessed() { processed.incrementAndGet(); }
    public int getProcessed()        { return processed.get(); }
}
```

Regulă de degetul mare:
- **un singur câmp simplu** → `AtomicInteger` / `AtomicLong` / `AtomicReference`,
- **mai multe câmpuri care trebuie să rămână consistente împreună** (ex.
  `ticketsLeft` + `nextTicketId`) → `ReentrantLock`.

### Ce câștigi în plus cu `ReentrantLock`

Nu trebuie să le folosești acum, dar e bine să știi că **există**:

```java
// timeout — clientul renunță dacă nu prinde lock în 50 ms
if (lock.tryLock(50, TimeUnit.MILLISECONDS)) {
    try { ... } finally { lock.unlock(); }
} else {
    // renunță
}

// interruptible — alt thread poate să-l "trezească" cu interrupt()
lock.lockInterruptibly();
```

`synchronized` nu poate face niciuna din astea.

### Aceleași 3 capcane la care să fii atent

1. **Mereu `try/finally`** — fără el, o excepție în secțiunea critică lasă
   lock-ul ținut pentru totdeauna.
2. **`lock()` în afara `try`** — pentru că `lock()` nu aruncă excepție; dar
   `lockInterruptibly()` da, deci pe acela îl pui în try.
3. **`final` pe field-ul `lock`** — fără el, riscul e să fie reasignat și
   thread-urile să se sincronizeze pe locks diferite (= nu se mai
   sincronizează deloc).

Același pattern se aplică și la ATM (vezi anexa similară din
`alex-atm-bank/atm-multithreading/src/ATM_Banking/CODE_REVIEW.md`).
