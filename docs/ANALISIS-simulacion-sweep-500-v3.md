# Análisis de simulación — sweep-500 (v3, instrumentación por encuentro)

> **Este análisis SUPERSEDE a `ANALISIS-simulacion-sweep-500-v2.md`** en todo lo que reanaliza:
> localización de la dificultad, reparto de derrotas por encuentro, divergencia entre políticas,
> y la atribución causal del lift de las cartas con *Weak*. Lo que v2 dice sobre economía del nodo
> (cartas de oro, nodos sin compra) no se reexamina aquí y sigue vigente.
> `ANALISIS-simulacion-sweep-500.md` (v1) sigue obsoleto y no debe usarse.

- **Dataset**: `app/build/simulation-output/sweep-500.csv`, regenerado el 2026-08-27 14:46 con la
  instrumentación por encuentro que v2 pidió como recomendación #1. 1000 runs
  (500 semillas `greedy` + 500 `leverage`, rango 0–499, emparejables por semilla).
  **Es una regeneración, no el mismo fichero que analizó v2**: el win rate agregado cambia
  ligeramente (69.6 % aquí frente a 69.8 % en v2) porque el arnés añadió columnas.
- **Fecha del análisis**: 2026-08-27.
- **Código de referencia verificado línea a línea**: `RunSimulator.kt`, `RunSimulationCsvExportTest.kt`,
  `RunManager.enterNode`/`advanceToNextCombat`, `NodePolicy.act`, `NodeConfig`, `DebtConfig`,
  `EnemyInstance.applyWeak`, `assets/enemies/all.json`, `assets/run/sequence.json`.

---

## 0. Metodología: dos correcciones que hacen el análisis posible

### 0.1 Reconstrucción del daño exacto por combate

`hpAfterCombatSeq` se muestrea **antes de la curación de nodo** (`RunSimulator` registra
`state.player.hp` en `Phase.NODE`, y `RunManager.enterNode` cura sobre el campo `hp` del
`RunManager`, no sobre el `PlayerState` del motor). Y la curación es **incondicional**:

```kotlin
private fun enterNode(freePickCount: Int) {
    nodeIndex++
    // Flat heal as part of the "rest", capped at max HP.
    hp = minOf(PlayerState().maxHp, hp + NodeConfig.HEAL_AMOUNT)   // HEAL_AMOUNT = 8
```

No es una acción del `NodePolicy`: se ejecuta siempre, antes de que la política decida nada.
Por tanto el HP de entrada a cada combate es determinista y se puede reconstruir:

```
hpEntrada[0] = 50
hpEntrada[i] = min(50, hpAfterCombat[i-1] + 8)      para i >= 1
dañoRecibido[i] = hpEntrada[i] - hpAfterCombat[i]
```

**Validación**: sobre 8477 combates la reconstrucción no produce ni un solo daño negativo, y todos
los valores caen exactamente sobre los patrones de intent declarados en `enemies/all.json`
(thug: 0/8; loan_shark: 0/9/12 —el 12 es `enrage_below_half`—; collector: 0/12/26).
Esto convierte el CSV en una medida directa de atrición, no en una inferencia.

### 0.2 Alineación por *slot*, no por ordinal

El collector forzado se inserta **sin avanzar `slotIndex`**, así que el ordinal i-ésimo de la
secuencia no identifica el encuentro. Cada run se alinea contra la secuencia base de
`sequence.json` resolviendo qué posición, al eliminarse, reproduce la base:

| slot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| enemigo | thug | thug | loan_shark | thug | loan_shark | loan_shark | collector | **collector (boss)** |

Las 1000 runs se alinean sin ambigüedad (7 secuencias distintas observadas). El collector forzado
se etiqueta `F`. **Este es el paso que cambia las conclusiones de v2**: v2 razonaba por ordinal
y `numCombats ∈ {8,9}`, lo que confunde "octavo combate" con "boss final".

Estadísticas de enemigos (verificadas contra `assets/enemies/all.json`; v2 citaba 24/40/56, que
es incorrecto):

| enemigo | HP | tier | tags | patrón de intents (cíclico) |
|---|---|---|---|---|
| `thug` | **22** | NORMAL | — | ATTACK 8 → ATTACK 8 → BUFF +3 |
| `loan_shark` | **36** | ELITE | `enrage_below_half` | ATTACK 9 → BUFF +3 → ATTACK 9 → DEBUFF 1 |
| `collector` | **52** | BOSS | `debuff_resist` | ATTACK 12 → MULTI 7×2 → **LEVY 5** → BUFF +4 → DEBUFF 2 |

---

## Resumen ejecutivo

| Métrica | greedy | leverage | total |
|---|---|---|---|
| Win rate | 71.2 % (IC95 67.1–75.0) | 68.0 % (IC95 63.8–71.9) | **69.6 %** (66.7–72.4) |
| Muertes en slots 1–6 | 0 | 0 | **0 / 1000** |
| Muertes en el collector **forzado** | 0 | 0 | **0 / 538** |
| Muertes en slot 7 (collector normal) | 25 | 36 | **61 / 1000 (hazard 6.1 %)** |
| Muertes en slot 8 (boss) | 119 | 124 | **243 / 939 (hazard 25.9 %)** |
| Turnos totales por run | 16.6 | 16.6 | 16.6 |

Tres frases:

1. **Toda la partida se decide por una desigualdad: `HP de entrada al boss ≥ 27`.** Esa regla
   sola clasifica correctamente **933 de 1000 runs (93.3 %)**. Por encima de 27 se gana el
   **99.7 %** de las veces (631/633); por debajo se gana el **21.2 %** (65/306).
2. **El umbral 27 no es emergente: es aritmética de la tabla de intents.** El collector solo hace
   daño en los turnos 1 y 2 de su ciclo (12 y 7×2=14); los turnos 3, 4 y 5 son LEVY, BUFF y DEBUFF,
   que hacen **cero daño**. Ningún combate observado llegó al turno 6. Resultado: en los **2477
   combates contra collector del dataset, el daño máximo es exactamente 26**, sin una sola excepción.
3. **v2 se equivocó al señalar al collector forzado como el punto de riesgo: mata 0 veces de 538.**
   El punto de riesgo infravalorado es el **collector del slot 7**, que v2 clasificó como
   "puro trámite" y que en realidad se lleva **61 derrotas (20.1 % del total)**.

---

## 1. Distribución por encuentro

### 1.1 Por slot ordinal

`daño` es el daño real reconstruido; `HP entrada` y `HP salida` son medias.

| slot | enemigo | n | muertes | hazard | turnos (media) | daño medio | daño p10/med/p90 | HP entrada | HP salida |
|---|---|---|---|---|---|---|---|---|---|
| 1 | thug | 1000 | 0 | 0.0 % | 1.72 | 5.73 | 0 / 8 / 8 | 50.0 | 44.3 |
| 2 | thug | 1000 | 0 | 0.0 % | 1.74 | 5.76 | 0 / 8 / 8 | 50.0 | 44.2 |
| 3 | loan_shark | 1000 | 0 | 0.0 % | 2.25 | 10.39 | 9 / 12 / 12 | 50.0 | 39.6 |
| 4 | thug | 1000 | 0 | 0.0 % | 1.23 | **1.77** | 0 / 0 / 8 | 47.4 | **45.7** |
| 5 | loan_shark | 1000 | 0 | 0.0 % | 1.81 | 8.03 | 0 / 9 / 12 | 46.3 | 38.2 |
| 6 | loan_shark | 1000 | 0 | 0.0 % | 1.85 | 8.12 | 0 / 9 / 12 | 43.3 | 35.2 |
| **F** | collector forzado | 538 | **0** | 0.0 % | 2.39 | 16.47 | 12 / 12 / 26 | 48.2 | 31.7 |
| **7** | collector | 1000 | **61** | **6.1 %** | 2.52 | **19.47** | 12 / **26** / 26 | 41.8 | 22.4 |
| **8** | collector (boss) | 939 | **243** | **25.9 %** | 2.38 | **19.42** | 12 / 23 / 26 | 31.8 | 12.4 |

### 1.2 Por `encounterId`

| enemigo | apariciones | turnos medios | daño medio | daño máx | muertes | % del daño total de la partida |
|---|---|---|---|---|---|---|
| `thug` | 3000 | 1.56 | 4.42 | **8** | 0 | 15.4 % |
| `loan_shark` | 3000 | 1.97 | 8.85 | **12** | 0 | 30.7 % |
| `collector` | 2477 | 2.44 | 18.80 | **26** | **304** | 53.9 % |

### 1.3 Qué encuentros son inofensivos, medido

**El slot 4 es HP positivo.** Cuesta 1.77 de daño medio (770 de 1000 runs lo matan en un turno sin
recibir nada) y va precedido de una curación de 8: el jugador **sale del slot 4 con más vida que
cuando entró al slot 3**. Es un encuentro que regala 6 HP netos.

**Los slots 1 y 2 son casi gratis**: 5.7 de daño cada uno, sin varianza real (el thug pega 8 o nada).

**Los seis primeros combates, en conjunto, no cobran nada**: infligen 39.8 HP de daño por run, y
el run devuelve 8 HP en cada uno de los nodos intermedios. El jugador entra al slot 7 con
**41.8 HP de media sobre 50** después de tres cuartas partes de la partida.

**La economía global de vida del run** confirma que el diseño está devolviendo casi todo lo que quita:

```
daño total infligido al jugador   : 86.4 HP / run
curación total concedida por nodos: 48.0 HP / run   (59.8 nominales; 11.8 se pierden por tope de 50)
ratio curación/daño               : 55.6 %
```

Más de la mitad del daño de la partida se reembolsa. Y ese reembolso está mal repartido: los
nodos de los slots 1–6 curan sobre un jugador que ya está casi lleno (se desperdician 2.2–3.3 HP
por tope en los nodos 1, 2 y 4), mientras que el nodo antes del boss cura **7.99 de 8** —
desperdicio 0.01. **La curación llega justo donde más rompe la tensión.**

---

## 2. ¿Es el collector forzado el único punto de riesgo?

**No. Es exactamente al revés: el collector forzado no mata a nadie.**

| Escenario | n | muertes en ese combate |
|---|---|---|
| Collector **forzado** (`F`) | 538 | **0** |
| Collector slot 7 | 1000 | **61** |
| Collector slot 8 (boss) | 939 | **243** |

El forzado entra con 48.2 HP de media —el jugador está casi lleno cuando la deuda cruza 30— y su
daño máximo es 26. **Es matemáticamente incapaz de matar**: haría falta entrar con ≤26, y ninguna
run lo hace (mínimo observado de HP de entrada a `F` en las 538 apariciones: **42**).

### 2.1 El encuentro infravalorado: el collector del slot 7

v2 lo metió en el bloque "combates 1–7 → 0 muertes, puro trámite". Con alineación por slot:

- **61 derrotas** (20.1 % de las 304 del dataset), hazard 6.1 %.
- Es el encuentro que **más daño medio inflige de toda la partida** (19.47, por encima del
  boss del slot 8 con 19.42).
- Reparto: 25 greedy / 36 leverage.
- Las 61 muertes ocurren **todas** en runs donde el break ya había disparado, y **56 de las 61**
  en runs donde disparó en el ordinal 5.

El mecanismo es de secuencia, no de potencia: cuando el break salta pronto, el jugador come
**dos collectors seguidos** (el forzado y el del slot 7) con un solo nodo de 8 HP entre medias.
Dos collectors a 26 son 52 de daño contra 8 de curación. Ahí es donde muere.

### 2.2 El verdadero segundo eje: 2 turnos contra 3 turnos en el slot 7

Como el collector solo pega en los turnos 1 y 2 de su ciclo, **su daño es una función escalón del
número de turnos que sobrevive**: 12 si muere en 2 turnos, 26 si aguanta 3 o más.

| turnos en slot 7 | n | daño | win rate |
|---|---|---|---|
| 2 | 510 | 12 | **85 %** |
| 3 | 450 | 26 | **54 %** |
| 4 | 35 | 26 | 51 % |

Controlando por la duración del boss para eliminar la causalidad inversa (en el slot 8 pocos
turnos significa que el jugador murió pronto, no que ganara rápido):

| turnos slot 7 | turnos slot 8 | n | win rate |
|---|---|---|---|
| 2 | 2 | 222 | **92.3 %** |
| 3 | 2 | 271 | **38.7 %** |
| 2 | 3 | 209 | 100 % |
| 3 | 3 | 124 | 100 % |

**54 puntos porcentuales dependen de un solo turno en el slot 7.** Ese turno es el dial de
dificultad más apretado que existe hoy en el juego, y es un artefacto: el jugador no está
eligiendo aguantar un turno más, está a merced de si su mano suma 52 de daño en dos turnos.

---

## 3. greedy contra leverage, por encuentro

HP a la salida de cada slot; `t` es el estadístico de la t emparejada por semilla
(leverage − greedy); "idénticos" es el porcentaje de semillas en que ambas políticas
terminan el slot con exactamente el mismo HP.

| slot | HP greedy | HP leverage | Δ | t emparejada | idénticos |
|---|---|---|---|---|---|
| 1 | 44.27 | 44.27 | 0.00 | 0.00 | **100 %** |
| 2 | 44.28 | 44.20 | −0.08 | −1.40 | 95 % |
| 3 | 39.67 | 39.54 | −0.13 | **−2.17** | 84 % |
| 4 | 45.72 | 45.61 | −0.10 | −0.64 | 70 % |
| 5 | 38.30 | 38.17 | −0.13 | −0.32 | 51 % |
| 6 | 35.41 | 34.90 | −0.51 | −1.11 | 38 % |
| F | 32.21 | 31.18 | −1.03 | −1.39 | 60 % |
| 7 | 23.00 | 21.72 | **−1.28** | **−2.29** | 36 % |
| 8 | 12.83 | 11.92 | −0.90 | −1.56 | 38 % |

**Respuesta al punto 3: divergen a partir del slot 2, pero nunca lo bastante como para importar.**

- El punto exacto de la primera divergencia es el **slot 2** (5 % de las semillas), y su causa es el
  nodo 1: es el primer sitio donde `chooseReward` puede diferir. En el slot 1 son idénticas al 100 %
  por construcción (no ha habido nodo todavía).
- La divergencia **estructural** (menos del 50 % de semillas idénticas) empieza en el **slot 5**.
- La divergencia **de magnitud** es despreciable: el máximo es **−1.28 HP en el slot 7**
  (significativo, t = −2.29, pero son 1.28 HP frente a un umbral de decisión situado en 27).
  Leverage llega al boss ~1 HP por detrás y con 0.7 más de daño recibido por collector.
- El signo es consistente: **leverage pierde en todos los slots a partir del 2**. No hay ningún
  tramo donde la política agresiva compre ventaja, ni siquiera transitoria. La identidad
  "Debt-as-Leverage" no aparece en ningún punto de la curva de HP.

Esto refuerza —ahora con granularidad, no por inferencia— el diagnóstico de v2 §4: la deuda la
fija el nodo, que es `policy`-agnóstico.

---

## 4. El punto de no retorno

### 4.1 El escalón de 27 HP

`P(muerte en el slot 8)` frente al HP con el que se entra al boss:

| HP entrada slot 8 | n | muere ahí | pierde la run |
|---|---|---|---|
| [0, 13) | 45 | **100 %** | 100 % |
| [13, 20) | 51 | 86.3 % | 86.3 % |
| [20, 27) | 210 | 72.4 % | 72.4 % |
| **[27, 34)** | **234** | **0.0 %** | **0.0 %** |
| [34, 41) | 128 | 1.6 % | 1.6 % |
| [41, 50] | 271 | 0.0 % | 0.0 % |

Fila a fila alrededor del umbral, sin agrupar:

```
HPin=24  n=45  muertes=32   71.1 %
HPin=25  n= 7  muertes= 6   85.7 %
HPin=26  n=27  muertes=19   70.4 %
HPin=27  n=40  muertes= 0    0.0 %   <-- discontinuidad
HPin=28  n=48  muertes= 0    0.0 %
```

El mismo escalón, en el mismo sitio, aparece en el slot 7:

```
HPin=26  n=22  muertes aqui=21   95.5 %
HPin=27  n=36  muertes aqui= 0    0.0 %
```

Las dos únicas muertes por encima de 27 (en `HPin=34`) no son muertes por HP: son las
**3 derrotas por Ejecución** de todo el dataset (`greedy` 382, `leverage` 417 y 484), donde el
`LEVY 5` del collector cruzó `EXECUTION_THRESHOLD = 50` con el jugador vivo.

### 4.2 Predictores tempranos

Correlación punto-biserial entre el HP al salir de cada slot y la victoria final:

| HP al salir de | r_pb | franja de riesgo |
|---|---|---|
| slot 3 | +0.038 | ninguna (33 % vs 27 %) |
| slot 4 | +0.325 | < 41 → **63 %** de derrota |
| slot 5 | +0.563 | < 25 → **80–96 %** de derrota |
| slot 6 | +0.612 | < 25 → **74–86 %** de derrota |
| slot 7 | **+0.704** | < 15 → **77–94 %** de derrota |

**El primer momento con poder predictivo real es la salida del slot 5** (mitad de la partida):
salir de ahí por debajo de 20 HP significa perder el 96 % de las veces (n=47). Salir con más de 41,
perder el 6 %. Antes del slot 4 no hay señal ninguna: el HP tras el slot 3 no predice nada
(r = 0.038), porque hasta ahí el juego es determinista.

### 4.3 El reloj de la deuda: cuándo salta el break

No hay deuda por encuentro en el CSV, pero **el ordinal en el que aparece el collector forzado es
un sello de tiempo exacto de cuándo la deuda cruzó 30**. Es el mejor predictor temprano disponible:

| break salta en | n | win rate | peakDebt | HP entrada slot 8 | % que llega ≥27 | muertes slot 7 |
|---|---|---|---|---|---|---|
| **nunca** | 462 | **91.6 %** (88.7–93.8) | 26.3 | **36.0** | 89.6 % | 0 |
| ordinal 4 | 7 | 71.4 % | 48.4 | 27.9 | 57.1 % | 0 |
| **ordinal 5** | 340 | **40.3 %** (35.2–45.6) | 38.9 | **26.2** | 39.1 % | **56** |
| ordinal 6 | 171 | 71.3 % (64.2–77.6) | 34.0 | 31.0 | 59.0 % | 5 |
| ordinal 7 | 20 | 45.0 % | 33.3 | 22.4 | 30.0 % | 0 |

**El punto de no retorno es "la deuda cruzó 30 antes del quinto combate".** Esa sola condición
mueve el win rate de 91.6 % a 40.3 % — **51 puntos**, más que cualquier carta, cualquier política y
cualquier otro factor medido en el dataset. Y el mecanismo es enteramente de HP, no de deuda:
el break inserta un collector de 16.5 de daño medio a cambio de un nodo de 8 de curación, y esos
~9 HP netos son los que colocan la media de entrada al boss en 26.2, es decir, **justo un punto por
debajo del escalón**.

Nótese que las runs sin break llegan al boss con 36.0 HP y ganan el 91.6 %: **para casi la mitad
del dataset el juego no tiene ningún punto de riesgo en absoluto.**

---

## 5. Corrección a v2: *Weak* no es el eje que decide las partidas

v2 concluyó que "gana Weak, no la Deuda" y recomendó ponerle precio. **Eso es un artefacto de
correlación.** `collector` tiene el tag `debuff_resist`, y:

```kotlin
fun applyWeak(turns: Int) {
    if (TAG_DEBUFF_RESIST in definition.tags) return   // EnemyInstance.kt:93-96
    weak += turns
}
```

**Weak es un no-op absoluto contra el collector**, y el collector causa el 100 % de las derrotas
(304/304). Weak no puede, mecánicamente, haber causado el lift que v2 midió.

La instrumentación nueva lo confirma, midiendo el efecto donde debería verse y no en el win rate:

| nº cartas Weak | n | WR | daño slot 3 (loan_shark, Weak **sí** aplica) | turnos slot 8 | daño slot 8 | HP entrada slot 8 |
|---|---|---|---|---|---|---|
| 0 | 286 | 55.2 % | 10.81 | 2.40 | 20.06 | 28.74 |
| 1 | 203 | 66.5 % | 10.43 | 2.32 | 19.37 | 29.76 |
| 2 | 234 | 68.8 % | 10.42 | 2.38 | 19.84 | 32.40 |
| 3 | 142 | 85.2 % | 10.01 | 2.41 | 18.69 | 34.11 |
| 4 | 84 | 88.1 % | 9.54 | 2.39 | 18.53 | 35.66 |

- Contra el único enemigo donde Weak funciona (`loan_shark`), pasar de 0 a 4 cartas de Weak ahorra
  **1.27 HP** en el slot 3. Sobre los tres loan_sharks del run, del orden de 3 HP — en un tramo
  donde nadie muere.
- Contra el boss, los turnos para matarlo **no bajan** (2.40 → 2.39) y el daño recibido baja
  1.5 HP, atribuible al daño bruto de las cartas, no al debuff.
- Lo que sí sube monótonamente es el **HP de entrada al boss** (28.7 → 35.7), que es la variable
  que realmente decide.

Y en el análisis carta a carta (runs con exactamente 6 picks, n=617), medido sobre HP de entrada al
boss en vez de sobre win rate, **el pool entero se aplana**:

| carta | runs | WR | Δ HP entrada boss (base 32.06) |
|---|---|---|---|
| `ghost_collector` | 263 | 81.7 % | **+2.09** |
| `repo_expert` | 319 | 76.8 % | +1.17 |
| `leverage_strike` | 197 | 77.7 % | +1.15 |
| `asset_bubble` | 157 | 77.7 % | +1.12 |
| … 15 cartas entre −1.2 y +0.6 … | | | |
| `foreclosure_express` | 283 | 64.7 % | −0.72 |
| `overdraft` | 108 | 67.6 % | −1.95 |
| `eternal_debt` | 147 | 68.0 % | −2.84 |
| `asset_auction` | 71 | 60.6 % | **−2.89** |

**El rango completo de las 23 cartas es de 4.98 HP.** El break, solo, mueve 9.8 HP (36.0 → 26.2).
La construcción del mazo pesa la mitad que un único evento del sistema de deuda.

> Consecuencia práctica: la recomendación #4 de v2 ("poner precio a Weak") **debe retirarse**.
> Weak está sobrevalorado en el análisis, no infravalorado en el diseño. Si acaso el problema es el
> contrario: `debuff_resist` en el boss hace que dos cartas del pool sean texto muerto en el único
> combate que importa, lo que es un problema de legibilidad para el jugador.

---

## 6. Recomendaciones de rebalance, priorizadas

### Metodología de las estimaciones

Cada run se **re-simula** sobre el daño y la secuencia observados, cambiando la constante y
recomputando la trayectoria de HP con el tope de 50 y la curación de nodo. Es una estimación de
primer orden: asume que el jugador y el enemigo mantienen su ritmo de turnos, cosa que dejará de
cumplirse con cambios grandes. **Calibración**: con los valores actuales (HEAL=8, Δ=0) el modelo
devuelve 69.9 % frente al 69.6 % observado, un error de +0.3 pp. Toda estimación por debajo de
~25 % debe tratarse como direccional, no como número.

Banda objetivo del GDD: **35–55 %**, tope duro 70 %.

| HEAL_AMOUNT \\ daño extra del collector | +0 | +4 | +6 | +8 |
|---|---|---|---|---|
| **8** (actual) | **69.9 %** | 45.9 % | 39.4 % | 27.3 % |
| **6** | 52.5 % | 34.5 % | 22.9 % | 18.3 % |
| **5** | 42.9 % | 25.6 % | 16.9 % | 12.0 % |
| **4** | 30.1 % | 15.0 % | 10.3 % | 6.0 % |

---

### #1 — Bajar `NodeConfig.HEAL_AMOUNT` de 8 a 5 (o 6)

**Impacto estimado: 69.9 % → 42.9 % (HEAL=5) o 52.5 % (HEAL=6). Dentro de banda con una constante.**

Es el único cambio de una sola línea que mete el win rate en la banda de diseño, y ataca la causa
raíz medida: el run devuelve el 55.6 % de todo el daño que inflige, y lo devuelve concentrado justo
antes del boss (desperdicio de curación en el nodo pre-boss: 0.01 de 8; en los nodos 1, 2 y 4:
2.2–3.3 de 8). Bajar la curación no desplaza el escalón de 27, pero **cambia cuántas runs caen a
cada lado**: hoy hay 234 runs en la franja [27,34), es decir, a menos de 7 HP de morir.

`HEAL_AMOUNT = 6` deja el win rate en 52.5 %, dentro de banda pero en el borde superior.
`HEAL_AMOUNT = 5` lo pone en 42.9 %, centrado. Recomendación: **5**, y medir.

Riesgo: es un nerf plano que afecta igual a las runs sin break (91.6 % de WR) y a las que lo
sufren (40.3 %), así que reduce el win rate sin reducir la **varianza**, que es el otro problema.
Por eso conviene combinarlo con #2.

---

### #2 — Rellenar los turnos muertos del ciclo de intents del collector

**Impacto estimado: 69.9 % → 45.9 % con +4 de daño efectivo por combate de collector.**

El problema estructural: de los 5 intents del collector, **tres seguidos hacen cero daño**.

```json
"intentPattern": [
  { "type": "ATTACK",       "damage": 12 },
  { "type": "MULTI_ATTACK", "damage": 7, "param": 2 },
  { "type": "LEVY",  "param": 5 },   // 0 daño
  { "type": "BUFF",  "param": 4 },   // 0 daño
  { "type": "DEBUFF","param": 2 } ]  // 0 daño
```

Consecuencia medida: **el daño máximo del collector es 26 en los 2477 combates del dataset**, y el
BUFF +4 de Strength **nunca llega a aplicarse a un ataque**, porque haría falta llegar al turno 6 y
el combate más largo observado duró 5 turnos (2 runs). Es una habilidad que no existe.

Opciones, de menor a mayor cirugía:

- **Dar daño al LEVY** (`{"type":"LEVY","damage":6,"param":5}`, si el motor lo soporta): elimina el
  turno 3 gratis, que es exactamente donde termina el **37.9 %** de los 2477 combates contra
  collector (y el 42.1 % llega al turno 3 o más allá).
- **Reordenar el ciclo** para que el BUFF caiga antes de un ataque
  (ATTACK → BUFF → MULTI → LEVY → DEBUFF): así el +4 sí multiplica, y el MULTI 7×2 pasa a 11×2.
- **No** subir simplemente el `damage: 12` del primer intent: eso castiga por igual a las runs de
  2 turnos y a las de 3, y **no cierra el turno gratis**, que es la anomalía real.

Beneficio adicional sobre #1: este cambio ataca la **varianza**, no solo la media. Hoy el 54 pp de
diferencia entre matar el slot 7 en 2 turnos o en 3 es puro azar de mano; con daño en los turnos
3–5 la penalización pasa a ser continua en vez de un escalón.

---

### #3 — Convertir el break en presión continua, o desplazarlo

**Impacto: es el factor de mayor varianza del dataset (51 pp), pero también el más delicado.**

El break, tal y como está, es un dado de dos caras: si salta en el ordinal 5 el win rate es 40.3 %,
si no salta es 91.6 %. Además el efecto **no es monótono** en el ordinal (5 → 40.3 %, 6 → 71.3 %,
7 → 45.0 %), lo que confirma que no es un eje de riesgo graduado sino un accidente de secuencia:
depende de cuántos collectors seguidos te toque comer.

Dos direcciones, **excluyentes**:

- **(a) Que el forzado no sea un collector.** Es la opción barata. Hoy `advanceToNextCombat` llama
  literalmente a `enemyById("collector")`, la misma definición de 52 HP y BOSS, sin escalar. Un
  enemigo forzado propio, más corto y con daño escalado por deuda, elimina el "dos collectors
  seguidos" que causa las 61 muertes del slot 7 y hace legible el castigo.
- **(b) Que el castigo por deuda sea continuo** en vez de un evento one-shot
  (`breakEncounterUsedThisRun`). Hoy el óptimo del jugador es "quedarse en 29", un punto de esquina
  —exactamente lo que el GDD dice querer evitar—.

**No tocar el valor 30 de `BREAK_THRESHOLD` sin hacer (a) o (b) antes**: mover el número desplaza
el escalón, no lo elimina.

---

### #4 — Cerrar la fuga de `EXECUTION_THRESHOLD`

**Impacto en win rate: bajo (~1 pp). Impacto en coherencia regla-código: alto.**

Confirmado con datos nuevos: **20 runs superan 50 de deuda (pico hasta 64) y 11 de ellas ganan.**
Solo **3 runs de 1000** mueren efectivamente por Ejecución, y las tres por el `LEVY` del collector,
que es el único camino donde `addDebt` se comprueba. El interés compuesto (15 %/turno, aplicado en
`beginTurn`) cruza los 50 sin disparar nada.

El GDD dice "Deuda por encima de 50 es derrota inmediata". Hoy es falso. Esto importa más de lo que
sugiere su efecto directo, porque **Ejecución es el freno que el GDD nombra como el dial a apretar
si el win rate supera el 70 %** — y ese dial está desconectado. Decidir cuál de las dos fuentes
manda es prerrequisito de cualquier calibración de la deuda.

---

### #5 — Revisar el slot 4 y la curación temprana

**Impacto estimado: bajo por sí solo (~2–3 pp), pero mejora la forma de la curva.**

El slot 4 (thug, 1.77 de daño medio, precedido de curación 8) es **HP-positivo**: el jugador sale de
él con 45.7, más de lo que tenía al entrar al slot 3 (50 → 39.6 → 45.7). Los slots 1, 2 y 4
desperdician entre 2.2 y 3.3 HP de curación cada uno contra el tope de 50.

Es el tramo donde el juego se declara sin consecuencias. Candidatos: subir el `thug` del slot 4 a
un `loan_shark` (el patrón `thug, thug, loan_shark, thug, ...` ya rompe la progresión de dificultad
en ese punto), o eliminar la curación de los nodos 1–3.

Si se aplica #1 (HEAL a 5), buena parte de esto se corrige solo; conviene medir antes de tocar
`sequence.json`.

---

### #6 — Retirada: no poner precio a Weak

La recomendación #4 de v2 se retira (ver §5). Si se toca algo del pool, el candidato con evidencia
en los datos nuevos es `asset_auction` (−2.89 HP de entrada al boss) y `eternal_debt` (−2.84), pero
**todo el pool cabe en 5 HP**: rebalancear cartas antes de arreglar el break y la curación es
optimizar en el eje que menos pesa.

---

## 7. Qué medir en el siguiente sweep

1. **Deuda por encuentro** (`debtAfterCombatSeq`). Es el único eje de la mecánica central que sigue
   sin instrumentar; hoy se infiere del ordinal del collector forzado, que es un proxy grueso.
2. **La acción tomada en cada nodo** (`nodeActionSeq`: buy / loan / repay / thin / freepick). v2
   dedujo "nodos sin compra" restando picks a nodos; con la rama 6 (`takeNodeFreePick`) esa resta
   no es correcta, y la economía del nodo es el sistema con más apalancamiento sin examinar.
3. **Bloqueo aplicado por combate**, para separar "el jugador bloqueó" de "el enemigo no pegó" en
   los valores intermedios de daño del slot 8 (19, 20, 23, 24…).

---

### Apéndice — reproducibilidad

Todas las cifras salen de `app/build/simulation-output/sweep-500.csv` (1000 filas, generado
2026-08-27 14:46), procesado con Python 3 de biblioteca estándar. El daño por combate se
reconstruye con `daño[i] = min(50, hpAfterCombat[i-1] + 8) − hpAfterCombat[i]`, válido porque
`RunManager.enterNode` cura incondicionalmente y `RunSimulator` muestrea pre-curación; la
reconstrucción se valida por ausencia de valores negativos (0 de 8477) y por coincidencia exacta
con los patrones de `assets/enemies/all.json`. La alineación slot↔ordinal se resuelve buscando la
posición cuya eliminación reproduce `sequence.json`; en las 20 runs con tres collectors consecutivos
la posición del forzado es ambigua entre los ordinales 7 y 8 y se asigna la más temprana (afecta
solo a la etiqueta, no al último combate, que siempre es el slot 8). Intervalos de confianza:
Wilson al 95 %. Contraste entre políticas: t emparejada por semilla. Los contrafactuales de la §6
son re-simulaciones de primer orden sobre la secuencia de daño observada, calibradas a +0.3 pp.

---

**Este documento supersede a `ANALISIS-simulacion-sweep-500-v2.md`** en: el reparto de derrotas por
encuentro (§2 y §3 de v2), la afirmación de que no hay muertes antes del combate final, la
identificación del collector forzado como punto de riesgo, la atribución causal del lift de *Weak*
(§2 de v2) y la recomendación #4 de v2. Las secciones de v2 sobre economía del nodo (cartas de oro,
acantilado de nodos sin compra) y sobre la divergencia de mazos entre políticas no se reexaminan
aquí y siguen vigentes.
