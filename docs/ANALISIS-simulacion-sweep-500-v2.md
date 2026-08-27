# Análisis de simulación — sweep-500 (v2, arnés corregido)

> **Este análisis SUPERSEDE a `ANALISIS-simulacion-sweep-500.md`**, que se hizo sobre un CSV generado
> por un arnés con tres bugs (mazo no expuesto → `chooseReward` nunca se invocaba, `turnsPerCombat`
> registrado una sola vez por run, y `pickedRewardIds` guardando la oferta en vez de la carta comprada).
> Aquel documento no debe usarse para tomar decisiones de balance.

- **Dataset**: `app/build/simulation-output/sweep-500.csv` — 1000 runs (500 semillas `greedy` +
  500 semillas `leverage`, rango 0–499 en ambas, emparejables por semilla).
- **Fecha**: 2026-08-27.
- **Código de referencia**: `RunManager.deckList`, `NodePolicy.act(run, policy)`,
  `RunSimulator` (los tres corregidos), `sequence.json` (8 slots), `DebtConfig`, `NodeConfig`,
  `all.json` (27 cartas = 4 starters + 23 de recompensa).
- **Constantes relevantes**: `maxHp = 50`, `BREAK_THRESHOLD = 30`, `EXECUTION_THRESHOLD = 50`,
  `INTEREST_RATE = 0.15`, `NodeConfig.HEAL_AMOUNT = 8`, `ESCALATION = 1.5`.
- Secuencia de encuentros: `thug, thug, loan_shark, thug, loan_shark, loan_shark, collector, collector`.

## Resumen ejecutivo

| Métrica | greedy | leverage | Criterio GDD |
|---|---|---|---|
| Win rate | **71.8 %** (359/500, IC95 67.9–75.7) | **67.8 %** (339/500, IC95 63.7–71.9) | 35–55 %, tope duro 70 % → **greedy FALLA** |
| Peak Debt medio (victorias) | 30.6 | 30.1 | > 25 → **PASA** (ambas) |
| Peak Debt medio (todas) | 32.4 | 31.8 | — |
| numCombats medio | 8.48 | 8.46 | 8 esperado (+1 si salta el break) |
| Turnos por combate | 1.95 | 1.97 | — |
| **Turnos totales por run** | **16.6** | **16.7** | **~35–45 → FALLA (≈40 % de lo previsto)** |
| HP final (victorias) | 17.0 / 50 | 16.1 / 50 | — |
| Derrotas contra `collector` | **141/141 = 100 %** | **161/161 = 100 %** | — |

Tres frases que resumen el hallazgo: **(1)** los siete primeros combates de la run no matan a nadie —
cero derrotas en 1000 runs; **(2)** el run entero dura ~16.6 turnos, menos de la mitad de los 35–45
que el GDD pedía para que el interés compuesto llegue a diferenciar nada; **(3)** el eje que decide
las partidas no es la Deuda, sino **Weak** (dos cartas de veintitrés) y **no perder un nodo de compra**.

---

## 1. Varianza real

### Win rate

Greedy 71.8 %, leverage 67.8 %. La diferencia de 4.0 pp **no es estadísticamente significativa**
en el test emparejado (McNemar χ²cc = 2.58, b = 80, c = 60, p ≥ 0.05). Greedy incumple el criterio 1
del GDD ("debe quedarse por debajo del 70 %"); leverage queda justo dentro por margen de ruido.

### Peak Debt

```
greedy   media 32.35  sd 7.82  p10 26  med 31  p90 44  max 75
leverage media 31.80  sd 7.49  p10 25  med 30  p90 43  max 75
```

La distribución **no es bimodal**, pero sí está fuertemente **concentrada y truncada por las
constantes del nodo**, no por decisiones de juego:

| Banda peak | greedy n | greedy WR | leverage n | leverage WR |
|---|---|---|---|---|
| [0,25) | 34 | 88.2 % | 35 | 82.9 % |
| **[25,30)** | **195** | **94.9 %** | **198** | **91.4 %** |
| **[30,35)** | **128** | **54.7 %** | **149** | **47.7 %** |
| [35,40) | 48 | 70.8 % | 42 | 71.4 % |
| [40,45) | 60 | 41.7 % | 45 | 31.1 % |
| [45,∞) | 35 | 42.9 % | 31 | 45.2 % |

El 39 % de las runs pican exactamente en la banda [25,30) y ganan el 92–95 %. **Justo al cruzar 30
el win rate se desploma 40 pp.** No es una curva de riesgo: es un escalón binario en
`BREAK_THRESHOLD`. El repunte de [35,40) es un artefacto de selección, no una recuperación real —
esas runs tienen más picks (6.83 de media frente a 6.43 en [30,35)); controlando por si saltó el
break, [30,35) con break = 66.0 % y [30,35) **sin** break = **8.0 %** (n=25), porque no saltar el
break teniendo deuda ≥30 significa que la deuda se disparó ya dentro del combate final.

### Runs degeneradas y outliers

- **Outliers Tukey altos**: 19 runs greedy / 31 leverage, con WR 47 % y 45 % (frente a 72 %/68 %).
- **`EXECUTION_THRESHOLD = 50` no se está aplicando de hecho**: hay **20 runs con peak > 50**
  (picos observados hasta **75**) y **la mitad de ellas gana**. El GDD dice que "Deuda por encima de
  50 es derrota inmediata"; la implementación solo dispara Ejecución en *acciones que aumentan deuda*,
  así que el **interés compuesto (15 %/turno) cruza la línea gratis**. Es una discrepancia
  regla-vs-código, no un caso raro tolerable.
- **Cohorte degenerada dura**: runs con ≤5 picks — **0/27 victorias en greedy, 2/33 en leverage**
  (0 % y 6 %). Ver sección 5: no es azar, es el nodo.

### numCombats y turnos

`numCombats` solo toma dos valores: **8** (no saltó el break) o **9** (saltó el forced collector).
Nunca más, porque `breakEncounterUsedThisRun` es one-shot. Reparto: greedy 258/242, leverage 270/230.

`avgTurnsPerCombat` ≈ 1.95, sd 0.20, rango 1.38–2.63. Con `thug` a 24 HP, `loan_shark` a 40 y
`collector` a 56, una media de **menos de dos turnos por combate** significa que los encuentros
normales se resuelven prácticamente en un turno. Correlación con victoria: **r = −0.41** (greedy) —
el mejor predictor continuo del CSV, y lo que mide en realidad es *cuánto se alarga el collector final*.

---

## 2. Cartas realmente elegidas

Las 23 cartas del pool de recompensa **se eligen todas al menos una vez**: no hay cartas muertas por
falta de oferta. Pero la concentración es alta: el top-5 se lleva el 44.5 % de los picks en greedy.

Lift estratificado **controlando por número de picks** (solo runs con exactamente 6 picks, que es la
moda; esto elimina el confusor dominante de la sección 5). `z` sobre diferencia de proporciones:

**greedy (n=312, base 73.4 %)**

| Carta | n | WR | lift | z | tags |
|---|---|---|---|---|---|
| `ghost_collector` | 153 | 81.0 % | **+15.0 pp** | +3.05 *** | — (Weak 2) |
| `repo_expert` | 186 | 78.0 % | **+11.3 pp** | +2.18 ** | — (Weak 1) |
| `leverage_strike` | 92 | 78.3 % | +6.9 pp | +1.31 | debt_scaling |
| `eternal_debt` | 52 | 78.8 % | +6.5 pp | +1.04 | add_debt |
| `asset_bubble` | 46 | 78.3 % | +5.7 pp | +0.86 | debt_payoff |
| … | | | | | |
| `foreclosure_express` | 164 | 67.1 % | **−13.3 pp** | −2.72 *** | — (+4 oro) |
| `overdraft` | 46 | 56.5 % | **−19.8 pp** | −2.55 ** | debt_draw |

**leverage (n=305, base 72.1 %)**

| Carta | n | WR | lift | z | tags |
|---|---|---|---|---|---|
| `ghost_collector` | 108 | 82.4 % | **+15.9 pp** | +3.20 *** | — (Weak 2) |
| `refinanciar` | 28 | 82.1 % | +11.0 pp | +1.43 | refinance |
| `partial_forgiveness` | 67 | 80.6 % | +10.8 pp | +1.91 * | — |
| `asset_bubble` | 110 | 78.2 % | +9.5 pp | +1.84 * | debt_payoff |
| `leverage_strike` | 105 | 78.1 % | +9.1 pp | +1.75 * | debt_scaling |
| … | | | | | |
| `foreclosure_express` | 118 | 63.6 % | **−14.0 pp** | −2.60 *** | — (+4 oro) |
| `eternal_debt` | 95 | 62.1 % | **−14.6 pp** | −2.52 ** | add_debt, debt_scaling |

### El hallazgo central: gana Weak, no la Deuda

`ghost_collector` y `repo_expert` son **las dos únicas cartas del pool de recompensa que aplican
Weak** (2 y 1 stacks respectivamente). Son también las dos únicas con `z > 2` positiva en **ambas**
políticas. `ghost_collector` hace 5 de daño — *menos* que `foreclosure_express` (6) — y aun así gana
15 pp más. La respuesta es dosis-dependiente y monótona:

| Cartas con Weak en el mazo | greedy n / WR | leverage n / WR |
|---|---|---|
| 0 | 109 / **57.8 %** | 179 / **54.7 %** |
| 1 | 180 / 63.9 % | 193 / 68.4 % |
| 2 | 139 / 83.5 % | 91 / 81.3 % |
| 3 | 59 / 88.1 % | 29 / 93.1 % |
| 4 | 12 / **100 %** | 8 / **100 %** |

Un rango de **42 pp** gobernado por 2 de 23 cartas. Y **sobrevive al control por picks**: dentro de
las runs sanas (1 nodo sin compra), greedy pasa de 82.4 % con 0 Weak a 98.0 % con 3 Weak.
`corr(nºWeak, avgTurnsPerCombat) = −0.39`: Weak acorta el combate final, que es donde se muere.

Ninguna de las dos `chooseReward` valora Weak (greedy ordena por daño; leverage por tags
debt_payoff/debt_scaling). **La simulación está por tanto midiendo un suelo, no un techo**: un jugador
humano que aprenda "coge las dos de Weak" está en la banda 88–100 %, no en el 72 %.

### Cartas que se eligen mucho y no correlacionan con ganar

- **`foreclosure_express`** (9.7 % de todos los picks en greedy, 3.º más elegido): **−13/−14 pp**,
  significativo en ambas políticas. Es la trampa de diseño más clara del pool. Da +4 oro, y el oro
  es activamente dañino (ver abajo).
- **`overdraft`** (debt_draw): −19.8 pp en greedy.
- **`eternal_debt`**: −14.6 pp en leverage pese a ser una de las cartas que el pivot "rescató".
- **`risky_investment`**: −5 a −13 pp; +12 oro a cambio de 6 de auto-daño.
- **`collateral_hold`**: 2.º más elegido por leverage (7.4 % de picks) y lift −2.1 pp. Es la carta que
  más define la identidad "leverage" y no aporta nada medible.

### El oro es una carta negativa

Cuatro cartas dan oro (`risky_investment` 12, `asset_auction` 9, `foreclosure_express` 4,
`reverse_mortgage` 4). La respuesta a la dosis es **monótonamente descendente**:

| Cartas con oro | greedy WR | leverage WR |
|---|---|---|
| 0 | 75.4 % | 70.9 % |
| 1 | 74.1 % | 70.1 % |
| 2 | 69.6 % | 63.2 % |
| 3 | 63.4 % | 50.0 % |
| 4 | 44.4 % | — |

Mecanismo probable: en `NodePolicy` la prioridad 3 es *repagar* cuando `debt >= 25 && gold >= debt + fee`.
Más oro dispara esa rama, que **consume el nodo entero sin añadir carta** — y perder un nodo de compra
es lo que mata (sección 5). El oro no es una recompensa: es un gatillo que gasta tu turno de tienda.

---

## 3. `defeatEncounterId`

**Sigue concentrada, y ahora al 100 %.**

| Política | Derrotas | Contra `collector` | Contra otro |
|---|---|---|---|
| greedy | 141 | **141 (100.0 %)** | 0 |
| leverage | 161 | **161 (100.0 %)** | 0 |

Cruzando con `numCombats`, ninguna derrota ocurre antes del octavo combate:

| Combate | Enemigo | Runs que entran | Muertes | Hazard |
|---|---|---|---|---|
| 1–7 | thug ×3, loan_shark ×3, collector | 500 | **0** | **0.0 %** |
| 8–9 | collector (slot final y/o forzado) | 500 | 141 / 161 | 28.2 % / 32.2 % |

**Cero muertes en los siete primeros combates de 1000 runs.** El *forced collector* que se inserta a
mitad de run cuando la deuda cruza 30 se sobrevive el 100 % de las veces que aparece en posición
intermedia. Los seis encuentros normales (thug 24 HP, loan_shark 40 HP) más el mid-boss son,
medidos, **puro trámite**.

---

## 4. ¿Divergen greedy y leverage?

**Sí en el mazo, no en el resultado ni en la deuda.** El fix funcionó mecánicamente pero no
produjo dos estrategias distinguibles por rendimiento.

### Divergen (el fix es real)

- Distribución de picks: **χ²(22) = 378.3**; distancia de variación total **0.212**.
- Jaccard medio entre mazos de la misma semilla: **0.466** (mediana 0.333).
- Secuencia de picks idéntica: solo **22.4 %** de las semillas (antes del fix habría sido ~100 %).
- Runs completamente idénticas (outcome + peak + nc + endHp + picks): **18.4 %**.
- Composición por tags:

| Tag | greedy | leverage | ratio |
|---|---|---|---|
| debt_payoff | 5.3 % | **14.5 %** | 2.74× |
| debt_scaling | 11.2 % | **20.0 %** | 1.79× |
| execution_damage | 1.5 % | 3.9 % | 2.55× |
| vanilla (sin tags) | **50.0 %** | 35.5 % | 0.71× |

`asset_bubble` 2.78×, `collateral_hold` 2.68×, `ejecucion` 2.55×, `eternal_debt` 2.42×: leverage
construye visiblemente el mazo de la identidad del pivot.

### No divergen (el problema)

- **Outcome discordante: 140/500 = 28.0 %** (80 gana solo greedy, 60 solo leverage). McNemar
  χ²cc = 2.58 → **no significativo**. Las dos políticas ganan lo mismo.
- **Peak Debt idéntico**: delta medio leverage−greedy = **−0.55** (t = −1.73, no significativo);
  el 39.6 % de las semillas dan *exactamente* el mismo pico y el 65.4 % quedan dentro de ±3.
  Leverage acumula *menos* deuda que greedy, al revés de lo que su nombre promete.
- Mismo `numCombats` en el **76.4 %** de las semillas.

**Causa**: la deuda no la genera el combate, la genera el **nodo**. `NodePolicy` es
*policy-agnostic* salvo en qué carta compra — el préstamo (prioridad 2: `gold < 20 && debt + loanDebt <= 45`),
el repago (prioridad 3) y el escalado `1.5^(n−1)` son iguales para las dos políticas y dominan la curva
de deuda. `LeveragePolicy.chooseAction` puede pedir deuda en combate todo lo que quiera: sobre un run
de 16 turnos, el préstamo del nodo ya la puso donde iba a estar. Esto también explica por qué la
aserción calibrada "leverage peak debt > greedy peak debt" quedó en rojo tras el fix: con el nodo
mandando, esa desigualdad ya no puede cumplirse por construcción.

---

## 5. ¿Dónde está la holgura del ~70 %?

**No es holgura generalizada: es holgura de fase, concentrada en los 7 primeros encuentros, con toda
la dificultad comprimida en un único combate final.**

### 5.1 La run dura la mitad de lo previsto

Turnos totales por run = `avgTurnsPerCombat × numCombats` = **16.6** (greedy) / **16.7** (leverage);
el 97 % de las runs quedan por debajo de 20 turnos. El GDD pedía **35–45 turnos** explícitamente para
que "la curva de interés compuesto llegue a diferenciar leverage temprano de leverage tardío".
A 15 %/turno sobre ~16 turnos repartidos en 8 combates, el interés nunca compone lo suficiente para
ser una amenaza: es un impuesto plano. **La mecánica económica central no tiene tiempo de existir.**

Aritmética de apoyo: `HEAL_AMOUNT = 8` × 7 nodos = **56 HP gratis** sobre un `maxHp` de 50. El run
regala más de una barra de vida completa. Los enemigos de los slots 1–6 no llegan a cobrarla.

### 5.2 La variable maestra: nodos sin compra

Nodos disponibles = `numCombats − 1`. Definiendo *nodos sin compra* = nodos − picks:

| Nodos sin compra | greedy n / WR | leverage n / WR |
|---|---|---|
| 0 | 15 / 86.7 % | 8 / 75.0 % |
| **1** | **363 / 88.4 %** | **386 / 81.9 %** |
| **2** | **120 / 20.8 %** | **102 / 14.7 %** |
| 4 | 2 / 0.0 % | 4 / 50.0 % |

Un solo nodo perdido (préstamo, repago o adelgazar mazo en lugar de comprar) es **prácticamente
neutro**; el segundo hunde la run 65 pp. No es una curva de coste de oportunidad: es un acantilado.
Y el detalle preocupante es que **quien pierde el nodo es la propia lógica de economía**, no el jugador:
las ramas de préstamo y repago de `NodePolicy` disparan por umbrales (`gold < 20`, `debt >= 25`) que
el propio oro ganado en combate activa.

### 5.3 El segundo acantilado: `BREAK_THRESHOLD = 30`

| | greedy | leverage |
|---|---|---|
| Break **no** salta (nc=8) | 258 runs, **84.1 %** WR | 270 runs, **78.1 %** WR |
| Break salta (nc=9) | 242 runs, **58.7 %** WR | 230 runs, **55.7 %** WR |
| Delta | **−25.4 pp** | **−22.5 pp** |

El forced collector concentra el **70.9 %** (greedy) y **63.4 %** (leverage) de todas las derrotas.

### 5.4 Los dos ejes juntos explican casi todo

| Break | ≥2 cartas Weak | greedy n / WR |
|---|---|---|
| No | **Sí** | 110 / **98.2 %** |
| No | No | 148 / 73.6 % |
| Sí | Sí | 101 / 72.3 % |
| Sí | No | 141 / **48.9 %** |

Existe una **línea dominante trivial de 98 %**: mantener la deuda bajo 30 y coger las dos cartas de
Weak. Es exactamente el fallo que el GDD anticipaba como riesgo abierto ("una línea óptima obvia
haría el eje de riesgo decorativo") y como criterio de parada ("si gana por encima de ~70 %, apretar
Ejecución antes de invertir más").

### Veredicto sobre la responsabilidad del exceso

Por orden de contribución medida:

1. **Los slots 1–6 no cobran nada** (0 muertes / 1000 runs) mientras el run regala 56 HP de curación.
   Toda la dificultad vive en 1 de 8 encuentros.
2. **La run es demasiado corta en turnos** (16.6 vs 35–45), así que interés y Deuda no llegan a
   ser un eje de riesgo: la Deuda es un número que sube hasta ~30 por el nodo y ahí se queda.
3. **Weak está sin precio**: 2 cartas de 23 valen 42 pp de win rate.
4. **`EXECUTION_THRESHOLD` no cierra por la vía del interés**: 20 runs pasan de 50 y la mitad gana.

---

## 6. Recomendaciones accionables (priorizadas)

Ninguna incluye números concretos de rebalance — eso es el trabajo de C8. Son las preguntas que
conviene resolver **antes** de tocar constantes, y qué medir para saberlo.

1. **Instrumentar `turnsPerCombat` por índice de encuentro y sacarlo al CSV, no solo su media.**
   Es el bloqueante metodológico de todo lo demás: hoy `avgTurnsPerCombat` mezcla un thug de 1 turno
   con el collector final, y **todas las conclusiones sobre "en qué tramo está la holgura" se están
   infiriendo de `numCombats` ∈ {8,9}**, que es un instrumento muy romo. Con el vector completo
   (y `hpPerCombat`) se podría medir directamente cuánto HP cobra cada slot, en vez de deducirlo.
   Coste bajo (el dato ya se calcula, solo se colapsa al escribir), retorno alto.

2. **Decidir si `EXECUTION_THRESHOLD` debe aplicarse también al tick de interés.**
   20/1000 runs superan 50 de Deuda (hasta 75) y **ganan el 50 %**. El GDD afirma "Deuda por encima
   de 50 es derrota inmediata"; el código solo la comprueba en acciones que aumentan deuda. Una de
   las dos fuentes está mal y hay que decidir cuál antes de calibrar nada, porque es precisamente
   el dial que el GDD nombra como el freno a usar si el win rate supera el 70 % — y ahora mismo ese
   dial tiene una fuga.

3. **Revisar la economía del nodo antes que las constantes de combate.**
   Es el sistema con más apalancamiento medido y el menos examinado: perder un segundo nodo de compra
   cuesta 65 pp, las cartas que dan oro reducen el win rate monótonamente (75 % → 44 %), y el préstamo
   del nodo domina la curva de Deuda hasta el punto de **borrar la diferencia entre las dos políticas**
   (delta de peak debt −0.55, no significativo). Mientras el nodo mande sobre la Deuda, ninguna
   política de combate puede expresar una identidad "leverage".

4. **Poner precio a Weak, o repartirlo.**
   `ghost_collector` y `repo_expert` son las 2 únicas cartas con Weak de 23, con lift +15/+11 pp
   (z > 2 en ambas políticas) y dosis-respuesta monótona de 58 % a 100 %. Y **ninguna de las dos
   `chooseReward` lo valora**, lo que significa que el 71.8 % medido es un *suelo*: un jugador que
   aprenda esto juega al 88–100 %. Cualquier calibración hecha contra el 71.8 % actual estará
   calibrando contra un jugador que no existe. Conviene además revisar `foreclosure_express`
   (3.ª carta más elegida, −13/−14 pp): es una trampa estadísticamente sólida.

5. **Tratar el escalón de `BREAK_THRESHOLD` como un problema de forma, no de valor.**
   El win rate cae de 94.9 % ([25,30)) a 54.7 % ([30,35)) y el 39 % de las runs se estacionan
   justo debajo. Mover el número desplazará el escalón, no lo eliminará: la pregunta de diseño es si
   la penalización por deuda debe ser un evento binario one-shot (`breakEncounterUsedThisRun`) o una
   presión continua. Hoy la respuesta óptima —"quedarse en 29"— es un punto de esquina, exactamente
   lo que el pivot decía querer evitar.

6. **Actualizar la aserción rota de `RunSimulationHarnessTest.kt:234` cuando se decida (3).**
   "leverage peak debt > greedy peak debt" ya no es alcanzable *por construcción*, no por
   descalibración: el préstamo del nodo es policy-agnostic y fija la deuda antes de que la política
   de combate opine. Es un síntoma correcto de un problema de diseño, y debe reescribirse como
   consecuencia de arreglar el nodo, no antes.

---

### Apéndice — reproducibilidad

Todas las cifras salen de `app/build/simulation-output/sweep-500.csv` (1000 filas) cruzado con
`app/src/main/assets/cards/all.json` y `app/src/main/assets/run/sequence.json`. Los tests de
significación son diferencia de proporciones (z) para lifts de carta, McNemar con corrección de
continuidad para el contraste emparejado de políticas, y t emparejada para el delta de peak debt.
El lift por carta se reporta **estratificado por número de picks** porque el número de picks es un
confusor de primer orden (r = +0.28 con victoria); los lifts sin estratificar exageran las cartas
tardías.
