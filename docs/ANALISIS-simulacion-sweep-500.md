# Análisis de balance — `sweep-500.csv` (1000 runs simuladas)

> **Qué es esto.** Análisis offline del dataset generado por
> `RunSimulationCsvExportTest` (`app/build/simulation-output/sweep-500.csv`):
> 500 seeds × 2 políticas (`greedy` = `ScriptedPolicy`, `leverage` = `LeveragePolicy`),
> mismo rango de seeds `0..499` en ambas, por tanto **comparables por pares**.
>
> **Fecha del análisis:** 2026-08-27. **Filas:** 1000 (verificado). **Herramienta:** `python3` (stdlib;
> pandas/numpy no están instalados en esta máquina).
>
> **Alcance:** solo lectura. Ningún fichero de código ni de git fue modificado.
>
> **Advertencia de lectura obligatoria:** la sección 0 documenta tres defectos de
> instrumentación que invalidan parcialmente tres de las nueve columnas del CSV.
> Las conclusiones de balance de las secciones 1–4 están formuladas teniendo en
> cuenta esas limitaciones, pero **cualquier decisión de tuning tomada sobre las
> columnas afectadas sin arreglarlas antes será una decisión tomada sobre ruido.**

---

## Resumen ejecutivo

| # | Hallazgo | Evidencia |
|---|---|---|
| **H1** | **La deuda está anticorrelacionada con la victoria.** El pico de deuda ≤29 gana el **80.0%**; ≥31 gana el **31.7%**. Es un acantilado, no una pendiente. | `r_pb(peakDebt, win) = −0.24 / −0.26`; n=426 vs n=524 |
| **H2** | **El 100% de las derrotas ocurren en `collector`.** Cero muertes en los 7 primeros encuentros. | 469/469 derrotas con `defeatEncounterId=collector` |
| **H3** | **Las dos políticas convergen: la mecánica central no diferencia runs.** 92.6% de acuerdo por seed, κ=0.85, 79.4% de seeds *bit-idénticas*. | McNemar χ²=2.70 (n.s.), y el signo **favorece a `greedy`** |
| **H4** | **`numCombats` vale 1 en las 1000 filas** y `avgTurnsPerCombat` mide solo el último combate. | Bug en `RunSimulator.kt:78,82` |
| **H5** | **`pickedRewardIds` es un log de *ofertas*, no de *elecciones*.** Distribución uniforme sobre las 23 cartas. | χ²=32.9 / 36.2 con df=22; ~161 apariciones por carta |
| **H6** | **`RunPolicy.chooseReward` es código muerto en el sweep.** Ambas políticas construyen el **mismo mazo**. | Cero llamadas desde `RunSimulator` |
| **H7** | **La economía del nodo colapsa a partir del nodo ~4-5** por la escalada `1.5^(n-1)` contra un presupuesto de oro plano. | Préstamo en nodo 5 exige `debt ≤ 10`; coste de compra 40→60→91 vs 110 de oro total |
| **H8** | **Ninguna carta tiene señal utilizable**, ni buena ni mala, tras corregir por comparaciones múltiples. | 0/23 cartas sobreviven a Bonferroni con n efectivo |

**Veredicto de los umbrales de diseño:** los tres umbrales del harness **pasan**,
pero **pasan por las razones equivocadas**. Ver sección 6.

---

## 0. Salud del dataset — tres defectos de instrumentación

Antes de interpretar nada. Verifiqué el código que genera el CSV
(`RunSimulator.kt`, `RunSimulationCsvExportTest.kt`, `NodePolicy.kt`, `RunManager.kt`).

### 0.1 `numCombats` no mide combates — vale 1 siempre

```
numCombats: {1: 1000}      # las 1000 filas
```

Causa raíz: `turnsPerCombat.add(...)` solo se ejecuta en las ramas terminales
`Phase.VICTORY` y `Phase.DEFEAT` de `RunSimulator.simulate()`
(`RunSimulator.kt:78` y `:82`). **No hay ningún `add` cuando termina un combate
intermedio.** La lista siempre acaba con exactamente un elemento.

Consecuencias:

- La columna `numCombats` **no aporta información** y no puede usarse como proxy
  de longitud de run.
- La columna `avgTurnsPerCombat` es en realidad **"turnos del último combate"**
  (siempre `avg` de una lista de un elemento). Por eso solo toma valores enteros
  `1.0 … 5.0`.
- `SimulationReport.avgTurnsPerCombat` hereda el mismo defecto, así que el
  criterio de longitud de run del GDD ("~35–45 turnos") **nunca se ha medido**.

**Proxy que sí funciona:** `len(pickedRewardIds)` cuenta visitas a nodo, y hay
un nodo tras cada combate ganado excepto el jefe final. Uso ese proxy en todo el
documento.

### 0.2 `pickedRewardIds` registra la carta **ofrecida**, no la elegida

En `RunSimulator.kt:71` la rama `Phase.NODE` hace:

```kotlin
val offerId = run.rewardChoices.firstOrNull()?.id ?: "node_no_free_pick"
NodePolicy.act(run)
pickedRewardIds.add(offerId)
```

Se registra la **primera oferta de free-pick** *antes* de que la política decida.
Pero `NodePolicy.act()` tiene una escalera de 6 prioridades y solo la última
(`takeNodeFreePick(run.rewardChoices.first())`) coge esa carta. Si la política
compra, la carta que entra al mazo sale de `nodeShopChoices` — una lista
**distinta**, con pesos por arquetipo (`archetypeBiasedOffer()`). Si presta,
repaga o hace thinning, **no entra ninguna carta al mazo** y aun así se registra
un id.

Prueba empírica de que es un log de ofertas y no de elecciones: `rewardChoices`
se construye con `.shuffled(rng).take(n)` sobre las 23 cartas no-starter, así que
la primera oferta debe ser **uniforme**. Y lo es:

| Política | Slots registrados | IDs distintos | Esperado/carta | χ² (df=22) | Crítico α=.05 |
|---|---|---|---|---|---|
| greedy | 3705 | 23/23 | 161.1 | **32.9** | 33.9 |
| leverage | 3709 | 23/23 | 161.3 | **36.2** | 33.9 |

Una distribución compatible con la uniforme. **Ninguna elección de jugador deja
esa huella.** Si la columna midiera decisiones, veríamos concentración en las
cartas que la política prefiere.

**Consecuencia crítica para la pregunta 2 (recursos útiles vs inútiles):** el
dataset actual **no puede responder "qué cartas se eligen"**. Puede responder
"qué cartas se ofrecen", y la respuesta es "todas por igual, por construcción".

### 0.3 `RunPolicy.chooseReward` nunca se ejecuta en el sweep

`RunSimulator` no llama a `policy.chooseReward` en ningún punto: la rama `NODE`
delega en `NodePolicy.act(run)`, que es un `object` compartido. Las únicas
llamadas a `chooseReward` en el repo son de tests unitarios que la invocan
directamente (`RunSimulationHarnessTest.kt:133-139`) y del alias
`RunManager.chooseReward` (`RunManager.kt:143`).

Esto significa que **la preferencia de draft consciente de C4 de `LeveragePolicy`
—preferir `debt_payoff` > `debt_scaling` > `debt_draw`— es código muerto durante
el sweep.** Ambas políticas construyen el mazo con exactamente la misma lógica.

Este es el mecanismo que explica H3 (sección 4): las políticas no pueden divergir
mucho porque solo se diferencian en el juego *dentro* del combate, nunca en la
composición del mazo.

### 0.4 Deriva de documentación detectada de paso

`DebtConfig.MAX_GARNISH_RATE` vale **0.6** en el código
(`DebtConfig.kt:34`), pero el GDD (`docs/GDD.md`, tabla de la economía de Deuda)
lo documenta como **0.75**. El código manda; el GDD tiene el bug.

---

## 1. Varianza — ¿decisiones o ruido?

### 1.1 Win rate

| Política | Victorias | Win rate | IC 95% |
|---|---|---|---|
| greedy | 271/500 | **54.2%** | [49.8%, 58.6%] |
| leverage | 260/500 | **52.0%** | [47.6%, 56.4%] |

> **Corrección del dato de referencia.** La cifra que traía la sesión anterior
> (greedy 54.4%, peak debt medio 31.9) no cuadra exactamente: el CSV da
> **54.2%** (271/500) y **32.12** de peak debt medio. Diferencia menor, pero el
> número correcto es 54.2/32.1.

`greedy` está a **0.8 puntos** del techo de la banda [35%, 55%], y el **límite
superior de su intervalo de confianza (58.6%) queda fuera de la banda**. No hay
margen: cualquier cambio que facilite el juego rompe el criterio.

### 1.2 Peak debt — cuantizado, no continuo

| Política | n | media | sd | min | p10 | p25 | mediana | p75 | p90 | max |
|---|---|---|---|---|---|---|---|---|---|---|
| greedy (todas) | 500 | 32.12 | 7.28 | 6 | 25 | 27 | 32 | 35 | 43 | 75 |
| greedy (victorias) | 271 | **30.49** | 6.94 | 21 | 25 | 27 | 29 | 33 | 40 | 75 |
| greedy (derrotas) | 229 | **34.04** | 7.20 | 6 | 27 | 30 | 32 | 37 | 44 | 60 |
| leverage (todas) | 500 | 32.28 | 7.61 | 6 | 25 | 27 | 32 | 35 | 43 | 75 |
| leverage (victorias) | 260 | **30.37** | 7.29 | 14 | 25 | 27 | 29 | 32 | 42 | 75 |
| leverage (derrotas) | 240 | **34.36** | 7.40 | 6 | 27 | 32 | 32 | 38 | 44 | 60 |

Dos cosas saltan a la vista:

1. **Las derrotas tienen más deuda que las victorias en ambas políticas**
   (34.0 vs 30.5 y 34.4 vs 30.4). Esto es lo contrario de la hipótesis de diseño.
   Ver sección 3.
2. **Las distribuciones de greedy y leverage son casi idénticas** — mismos
   percentiles, y los valores exactos más frecuentes coinciden carta por carta:
   `32` aparece 86 veces en ambas, `27` 61 veces en ambas, `29` 58 vs 57.
   El pico de deuda **no lo determina la política de combate**, lo determina
   `NodePolicy` (compartido).

**La distribución está cuantizada**, no es continua. Los valores modales (27, 29,
32) y los huecos (24, 31, 39) vienen de que la deuda se mueve a saltos discretos:
`LOAN_DEBT_BASE = 8` escalado `1.5^(n-1)` → **+8, +12, +18…** por préstamo de
nodo, más el `LEVY +5` del `collector`. Hay una leve bimodalidad
(35-39: 84 runs → 40-44: 102 runs) que es un artefacto de esos escalones, no una
señal de dos estrategias distintas.

### 1.3 HP final en victorias — todas las victorias son ajustadas

| Política | n | media | sd | mediana | p90 | max | ≤5 HP | ≤10 HP |
|---|---|---|---|---|---|---|---|---|
| greedy | 271 | 11.28 | 8.47 | 9 | 22 | 34 | 78 (28.8%) | 140 (51.7%) |
| leverage | 260 | 11.34 | 8.44 | 9 | 22 | 38 | 75 (28.8%) | 132 (50.8%) |

Con `PlayerState.maxHp = 50`: **la mediana de victoria termina con el 18% de la
vida**, más de la mitad de las victorias acaban por debajo del 20% de HP, y
**ninguna run en las 1000 termina por encima de 38 HP**. No existe la "victoria
cómoda". Esto es varianza de al filo, no varianza de decisión.

### 1.4 Turnos (última pelea) — degenerada

Recordatorio: por el bug 0.1 esto mide solo el combate final.

| Política | Resultado | 1 turno | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| greedy | VICTORY | 2 | 78 | 145 | 40 | 6 |
| greedy | **DEFEAT** | **27** | **202** | 0 | 0 | 0 |
| leverage | VICTORY | 1 | 74 | 142 | 38 | 5 |
| leverage | **DEFEAT** | **34** | **206** | 0 | 0 | 0 |

**El 100% de las derrotas se resuelven en ≤2 turnos de la pelea final.** Ninguna
derrota dura 3 turnos o más. No hay "pelea larga que se pierde por desgaste": o
sobrevives al primer intercambio con el `collector` o mueres inmediatamente. El
jugador no tiene ventana para reaccionar, y desde luego no tiene los ~3-4 turnos
que la mecánica de Leverage necesitaría para acumular deuda y convertirla en daño.

### 1.5 Runaways y outliers

- **Cero runs alcanzaron el guardarraíl `maxActionsPerRun = 500`.** Las 1000
  filas existen; una run desbocada habría lanzado `IllegalStateException` y
  abortado el export. El guardarraíl está sano y no está enmascarando nada.
- **21 runs superaron `EXECUTION_THRESHOLD = 50` sin morir por Ejecución**, y
  **9 de ellas ganaron** (12 derrotas; máximo `peakDebt = 75`, seed 413, victoria
  en ambas políticas). No es un bug: `CombatEngine.addDebt` (`:53-61`) excluye
  deliberadamente el tick de interés del chequeo de Ejecución (Decisión B). Pero
  **la línea de la muerte es porosa**: con interés compuesto al 15%/turno, un
  jugador por encima de 50 sigue vivo mientras no juegue nada que suba deuda.
- **10 runs con `peakDebt ≤ 14`** (nunca apalancaron): 8 derrotas, 2 victorias.
  Son casos donde el jugador murió antes de que `NodePolicy` pudiera prestar.
- **85 runs con `peakDebt < 30` que aun así perdieron.**

### 1.6 Veredicto sobre la varianza

**La varianza marginal parece sana; la varianza atribuible a la decisión del
jugador es casi nula.** Los histogramas tienen dispersión (sd de peak debt ~7.3,
sd de HP final ~8.5), pero la sección 4 demuestra por pares que cambiar por
completo la estrategia de deuda del jugador **cambia el resultado en solo el 7.4%
de las seeds**. La varianza que se ve es varianza de RNG (orden de robo, ofertas),
no varianza de agencia.

No es "todo o nada" bimodal en el sentido clásico, pero es **degenerada en el
sentido que importa**: el resultado lo decide la semilla, no el jugador.

---

## 2. Recursos útiles vs inútiles

> **Precaución metodológica (ver 0.2).** Lo que sigue analiza la columna
> `pickedRewardIds`, que es un log de **ofertas uniformes al azar**, no de
> elecciones. El canal causal entre "esta carta apareció en el log" y "la run se
> ganó" es casi inexistente: solo cuando `NodePolicy` cae al caso 6 (free pick)
> la carta registrada entra realmente al mazo. **Los números de abajo son, en su
> mayor parte, ruido de RNG, y así hay que leerlos.**

### 2.1 Frecuencia — plana por construcción

Las 23 cartas del pool no-starter aparecen todas, entre 128 y 187 veces sobre
~161 esperadas. **No hay ninguna carta "que casi nunca se elige"** en estos datos,
y eso no es una buena noticia: es la firma de que nadie está eligiendo.

### 2.2 Correlación carta ↔ victoria, con corrección

Solo dos cartas muestran una señal consistente en signo **y** magnitud a través
de las dos políticas:

| Carta | greedy Δ | greedy z | leverage Δ | leverage z | pooled z | z efectivo¹ | p efectivo | ¿Bonferroni²? |
|---|---|---|---|---|---|---|---|---|
| `mortgage_collateral` | −11.7pp | −2.43 | −13.2pp | −2.73 | −3.65 | **−2.58** | 0.010 | **NO** |
| `repo_expert` | +11.2pp | +2.33 | +11.1pp | +2.30 | +3.27 | **+2.31** | 0.021 | **NO** |
| `bounced_check` | +10.0pp | +2.02 | +8.2pp | +1.65 | +2.60 | +1.84 | 0.066 | NO |
| `compound_interest` | −3.8pp | −0.78 | −9.2pp | −1.85 | −1.87 | −1.32 | 0.187 | NO |
| `collateral_hold` | −4.8pp | −0.98 | −6.7pp | −1.36 | −1.65 | −1.17 | 0.242 | NO |
| `leverage_strike` | +2.7pp | +0.55 | +1.5pp | +0.31 | +0.61 | +0.43 | 0.669 | NO |
| `asset_bubble` | +0.5pp | +0.09 | +2.5pp | +0.47 | +0.40 | +0.28 | 0.777 | NO |
| `ejecucion` | −3.1pp | −0.61 | −0.2pp | −0.04 | −0.47 | −0.33 | 0.742 | NO |

¹ *z efectivo* = `z_pooled / √2`. Necesario porque los logs de oferta de greedy y
leverage son **idénticos en 439 de 500 seeds**: agrupar las dos políticas no
duplica la información, solo duplica el n nominal.
² Umbral de Bonferroni para 23 cartas: α = 0.05/23 = **0.0022**.

**Resultado: 0 de 23 cartas sobreviven a la corrección por comparaciones
múltiples.** Con 23 pruebas a α=0.05 se esperan ~1.2 falsos positivos por
casualidad; observamos 3 (greedy) y 2 (leverage). Está dentro de lo esperado por
azar.

### 2.3 Lo que sí se puede decir con honestidad

- **No hay carta trampa identificable ni carta dominante identificable** en este
  dataset. Las candidatas más plausibles a mirar cuando la instrumentación esté
  arreglada son `mortgage_collateral` (SKILL, coste 1, **12 de bloque**, sin tag
  de economía — el mayor bloque bruto del pool y aun así el peor indicador) y
  `repo_expert` (ATTACK, coste 1, **7 de daño**, sin tags — el mejor ataque
  vainilla del pool). Que la mejor y la peor señal sean precisamente las **dos
  cartas sin identidad de economía** es una pista interesante, no una conclusión.
- **La tabla de payoff de C4 no tiene ningún efecto medible.** `ejecucion`
  (z=−0.33), `asset_bubble` (z=+0.28), `leverage_strike` (z=+0.43) y
  `collateral_hold` (z=−1.17) son indistinguibles de cero. Consistente con la
  sección 4: las cartas de apalancamiento apenas entran al mazo, y cuando entran
  el combate acaba antes de que rindan.
- **La pregunta abierta del GDD sobre `bounced_check` vs `foreclosure_express`
  sigue abierta.** `bounced_check` sale +10.0/+8.2pp y `foreclosure_express`
  +2.7/+3.2pp, pero ambos dentro del ruido. **Este dataset no cierra esa
  decisión de C8.**

---

## 3. `defeatEncounterId` — un solo encuentro concentra el 100% de las derrotas

```
greedy   : collector 229/229 = 100.0%
leverage : collector 240/240 = 100.0%
TOTAL    : collector 469/469 = 100.0%
```

**No hay una sola derrota en `thug` ni en `loan_shark`** sobre 1000 runs. La
secuencia (`assets/run/sequence.json`) es:

| Slot | Enemigo | HP | Oro |
|---|---|---|---|
| 1 | thug | 22 | 10 |
| 2 | thug | 22 | 10 |
| 3 | loan_shark | 36 | 15 |
| 4 | thug | 22 | 12 |
| 5 | loan_shark | 36 | 18 |
| 6 | loan_shark | 36 | 20 |
| 7 | collector | 52 | 25 |
| 8 | collector | 52 | 30 |

Confirmado por el proxy de longitud: `len(pickedRewardIds)` vale **7 u 8 en las
1000 runs, nunca menos**. Es decir, **todas las runs, ganadoras y perdedoras,
llegan al final de la secuencia.** Los seis primeros encuentros tienen una tasa
de mortalidad exacta del **0%**.

Esto es un fallo estructural de curva de dificultad, no un pico mal calibrado:
**los 6 primeros combates son un trámite y la run entera se decide en el
`collector`**, en ≤2 turnos (sección 1.4).

### 3.1 El encuentro forzado agrava el problema

`RunManager.refresh()` arma `pendingBreakEncounter` cuando `debt >= BREAK_THRESHOLD`
(30), lo que inserta un `collector` extra **sin avanzar `slotIndex`** — visible en
el CSV como una octava visita a nodo.

| Política | Nodos | n | Win rate | peak debt medio |
|---|---|---|---|---|
| greedy | 7 (sin break) | 295 | **60.3%** | 28.4 |
| greedy | 8 (con break) | 205 | **45.4%** | 37.4 |
| leverage | 7 (sin break) | 291 | **60.1%** | 28.5 |
| leverage | 8 (con break) | 209 | **40.7%** | 37.5 |

Diferencia de **−15.0pp** (greedy, z=3.31, p=0.0009) y **−19.5pp** (leverage,
z=4.30, p<0.0001). Cruzar deuda 30 te añade un **tercer jefe** a una run que ya
concentra el 100% de su letalidad en jefes.

---

## 4. ¿Funciona la mecánica central? — No

Esta es la pregunta que más importa y la respuesta es la peor posible.

### 4.1 Comparación pareada por seed

Misma seed = mismo mazo inicial, misma secuencia de enemigos, mismo RNG. Lo único
que cambia es la política de deuda.

| | leverage GANA | leverage PIERDE |
|---|---|---|
| **greedy GANA** | 247 | 24 |
| **greedy PIERDE** | 13 | 216 |

- **Acuerdo de resultado: 463/500 = 92.6%**
- **κ de Cohen = 0.852** (acuerdo "casi perfecto" en la escala Landis-Koch)
- **McNemar** (corrección de continuidad): discordantes = 37 (24 vs 13),
  χ² = **2.703**, **p > 0.05 → no significativo**
- Y el signo de la diferencia **favorece a `greedy`**: hay casi el doble de seeds
  que solo gana la política conservadora (24) que las que solo gana la agresiva (13).

### 4.2 Las runs no son cualitativamente distintas

| Métrica | Seeds con valor idéntico en ambas políticas |
|---|---|
| `peakDebt` | 412/500 (82.4%) |
| `endHp` | 451/500 (90.2%) |
| `avgTurnsPerCombat` | 463/500 (92.6%) |
| `pickedRewardIds` (log completo) | 439/500 (87.8%) |
| **Todas las métricas a la vez (run bit-idéntica)** | **397/500 (79.4%)** |

Deltas medios (leverage − greedy): `peakDebt` **+0.16** (sd 4.47), `endHp`
**−0.22**, turnos **−0.03**. Todos indistinguibles de cero.

**Casi 4 de cada 5 seeds producen una run byte a byte idéntica bajo las dos
políticas.** Y `defeatEncounterId` es `collector` en el 100% de los casos en
ambas, así que tampoco cambia el encuentro fatal.

### 4.3 Causa raíz

Hay tres mecanismos encadenados, y ninguno es "la hipótesis de Leverage es
falsa" — son fallos de arnés y de economía que impiden **probar** la hipótesis:

1. **`chooseReward` es código muerto (0.3).** Las dos políticas construyen el
   mismo mazo vía `NodePolicy`. `LeveragePolicy` nunca llega a draftear cartas de
   apalancamiento. Solo puede diferenciarse jugando distinto un mazo idéntico.
2. **`NodePolicy` fuerza la deuda en ambas.** `LOAN_GOLD_NEED = 20`,
   `SAFE_AFTER_LOAN = 45`: el nodo pide préstamos agresivamente sin importar la
   política de combate. Por eso el peak debt medio es 32.1 vs 32.3 — **la deuda
   la genera el nodo, no la estrategia.**
3. **El combate acaba demasiado pronto para que Leverage rinda.** Con
   `LEVERAGE_DIVISOR = 5`, un Strike a deuda 30 pega 12 en vez de 6. Pero las
   derrotas se resuelven en ≤2 turnos y `LeveragePolicy` gasta sus primeros
   turnos comprando deuda (rama `state.debt < 15` → juega el préstamo *antes* que
   el ataque). Paga el coste de tempo y muere antes de cobrar el beneficio.

### 4.4 La deuda es un lastre, no un camino — el acantilado en 30

Tasa de victoria por valor exacto de `peakDebt` (pooled, 1000 runs, valores con
n ≥ 5):

| peak | n | win% | | peak | n | win% |
|---|---|---|---|---|---|---|
| 21 | 11 | 81.8% | | **32** | **172** | **17.4%** |
| 22 | 15 | 100.0% | | 33 | 40 | 45.0% |
| 23 | 26 | 84.6% | | 34 | 58 | 37.9% |
| 25 | 45 | 75.6% | | 35 | 40 | 25.0% |
| 26 | 55 | 80.0% | | 38 | 16 | 25.0% |
| 27 | 122 | 85.2% | | 42 | 29 | 31.0% |
| 28 | 25 | 88.0% | | 44 | 27 | 18.5% |
| 29 | 115 | 75.7% | | 48 | 10 | 20.0% |
| **30** | **50** | **48.0%** | | 51 | 5 | 20.0% |

Agregado:

| Banda | n | Win rate |
|---|---|---|
| `peakDebt ≤ 29` | 426 | **80.0%** |
| `peakDebt == 30` | 50 | **48.0%** |
| `peakDebt ≥ 31` | 524 | **31.7%** |

**El win rate cae 48 puntos al cruzar exactamente `BREAK_THRESHOLD = 30.`**
Point-biserial `r(peakDebt, win)` = **−0.243** (greedy) / **−0.262** (leverage).

El valor de peak debt más común de todo el dataset (**32**, 172 runs) es también
uno de los peores (**17.4%** de victoria). Y 32 no es casual: es `24 + 8` (primer
préstamo de nodo) o `27 + 5` (LEVY del collector). **La economía empuja al
jugador justo al otro lado del acantilado.**

El diseño quiere que el óptimo de deuda sea un punto interior que haya que
calcular bajo presión. Hoy el óptimo es una esquina trivial: **quédate en 29.**

### 4.5 Por qué el acantilado está en 30: triple penalización apilada

Cruzar 30 dispara **tres** castigos a la vez y **ninguna** recompensa:

1. **Encuentro `collector` forzado extra** (`pendingBreakEncounter`) → −15 a −19pp.
2. **Embargo al máximo**: `garnishAmount` topa en `MAX_GARNISH_RATE = 0.6` justo
   en `debt >= 30`. El jugador pierde el **60%** de todo el oro restante.
3. **Espiral de repago**: `repayViaNode()` exige `gold >= debt + fee`. Con el 60%
   embargado, el jugador nunca vuelve a acumular oro suficiente para salir.

El bonus de Leverage a cambio es `floor(30/5) = +6` de daño por ataque. No compensa
ni de lejos.

> **Sub-hallazgo:** las runs con `peakDebt ≥ 30` pero **solo 7 nodos** ganan un
> **7.5%** (n=160). Son runs que cruzaron 30 *durante el jefe final* (típicamente
> por el `LEVY +5`), demasiado tarde para que el break encounter llegue a
> dispararse. Es causalidad inversa: están perdiendo, y perder les sube la deuda.

---

## 5. La economía del nodo colapsa a mitad de run

Hallazgo colateral que explica varios de los anteriores.
`NodeConfig.escalatedCost(base, n) = base × 1.5^(n-1)`:

| Nodo | Préstamo (+deuda) | Préstamo (+oro) | Comprar | Fee repago | Eliminar |
|---|---|---|---|---|---|
| 1 | 8 | 12 | 8 | 3 | 10 |
| 2 | 12 | 18 | 12 | 4 | 15 |
| 3 | 18 | 27 | 18 | 6 | 22 |
| 4 | 27 | 40 | 27 | 10 | 33 |
| 5 | **40** | 60 | **40** | 15 | 50 |
| 6 | **60** | 91 | **60** | 22 | 75 |
| 7 | **91** | 136 | **91** | 34 | 113 |

Contra esto, el **oro total** que la secuencia reparte antes del jefe final es
`10+10+15+12+18+20+25 = 110` — **plano**, sin escalar. Y con deuda ≥30 el embargo
se lleva el 60%, dejando ~44 de oro efectivo para toda la run.

Consecuencias directas:

- **`takeLoan()` muere en el nodo 5**: exige `debt + 40 ≤ 50`, es decir
  `debt ≤ 10`. Con una mediana de peak debt de 32, prácticamente nunca se cumple.
  En los nodos 6 y 7 es matemáticamente imposible (`+60`, `+91` > 50).
- **`buyCard()` muere hacia el nodo 4-5**: 27 y 40 de oro contra ~44 disponibles
  en total.
- **`removeCardFromDeck()` (thinning) muere en el nodo 4**: cuesta 33, y
  `NodePolicy` solo lo intenta a partir de `THIN_NODE = 4`. **La regla 4 de
  `NodePolicy` es inalcanzable por construcción.**
- **`repayViaNode()`** casi nunca es asequible cuando `NodePolicy` lo quiere
  (`REPAY_BAND = 25`), porque a esa deuda el embargo ya está a tope.

Es decir: **de las 6 prioridades de `NodePolicy`, en la segunda mitad de la run
solo queda viable la 6 (free pick).** El nodo entre combates —la entrega
completa de C7— es decorativo a partir del nodo 4. Y de rebote, esto explica por
qué el log de ofertas de 0.2 se parece tanto a un log de elecciones en los nodos
tardíos: allí *sí* se coge la primera oferta, porque no hay otra cosa que hacer.

---

## 6. Estado frente a los criterios de éxito del GDD

| Criterio | greedy | leverage | ¿Pasa? | Lectura honesta |
|---|---|---|---|---|
| Win rate en [35%, 55%] | 54.2% | 52.0% | ✅ | **Al filo.** El IC superior de greedy (58.6%) se sale de la banda. |
| Ninguna política ≥70% | 54.2% | 52.0% | ✅ | Holgado. |
| Peak debt medio en victorias > 25 | 30.5 | 30.4 | ✅ | **Falso positivo.** Pasa porque `NodePolicy` está cableada para pedir préstamos, no porque apalancarse gane. Con peak ≤29 se gana el **80%**; con ≥31, el **31.7%**. La métrica mide el arnés, no el juego. |
| ≥2 arquetipos distintos en mazos ganadores | — | — | ❌ **No medible** | `chooseReward` es código muerto (0.3) y `pickedRewardIds` es un log de ofertas (0.2). El dataset no contiene la composición del mazo final. |
| Longitud de run 12-18 min (~35-45 turnos) | — | — | ❌ **No medible** | `numCombats` vale 1 y `avgTurnsPerCombat` mide un solo combate (0.1). |

**El resumen incómodo: los tres criterios que "pasan" son exactamente los tres que
se miden con columnas fiables, y el tercero pasa por circularidad. Los dos
criterios que tocan el corazón del pivot (diversidad de arquetipos, longitud de
run) no se han medido nunca.**

---

## 7. Recomendaciones priorizadas

### P0 — Arreglar la instrumentación antes de tocar una sola constante

**Nada de lo que sigue se puede validar con el arnés actual.** Tres cambios en
`app/src/test/java/com/debtsdecks/core/simulation/`:

1. **`RunSimulator.kt`** — añadir `turnsPerCombat.add(...)` cuando termina cada
   combate, no solo en las ramas terminales (`:78`, `:82`). Hoy `numCombats` vale
   1 en 1000/1000 filas y `avgTurnsPerCombat` mide un único combate.
2. **`RunSimulator.kt:71`** — registrar la **acción real del nodo** y la carta que
   realmente entró al mazo, no `rewardChoices.first()`. Sugerencia de columnas:
   `nodeActions` (`buy|loan|repay|remove|freepick` por nodo) y `finalDeck`.
   Sin esto la pregunta "qué cartas son útiles" es irrespondible: hoy el log es
   uniforme sobre 23 cartas (χ²=32.9, df=22).
3. **Decidir sobre `RunPolicy.chooseReward`**: o se cablea en la rama `NODE` para
   que las políticas construyan mazos distintos, o se borra de la interfaz. Hoy
   es código muerto con dos implementaciones cuidadas (la de `LeveragePolicy`
   incluso es consciente de C4) que nunca se ejecutan.

*Coste bajo, desbloquea todo lo demás.*

### P1 — Rebalancear la curva de dificultad: el juego son 6 combates de trámite y un jefe

**469 de 469 derrotas ocurren en `collector`; cero en `thug` y `loan_shark`.** El
100% de esas derrotas se resuelve en **≤2 turnos**.

- Subir la presión de los slots 1-6 (hoy `thug` 22 HP y `loan_shark` 36 HP contra
  un jugador de 50 HP que además cura `HEAL_AMOUNT = 8` en cada nodo), **o**
  bajar la letalidad de salida del `collector` (52 HP, `12 dmg` / `9×2` multi).
- El objetivo no es subir la dificultad global —el win rate ya está al filo de la
  banda— sino **redistribuirla**. Cambiar mortalidad del jefe por mortalidad
  temprana deja el win rate donde está y hace que los nodos importen.
- Alargar el combate final es condición necesaria para que Leverage tenga sentido:
  una mecánica de acumulación no puede rendir en 2 turnos.

### P2 — Desmontar el acantilado de deuda 30

**`peakDebt ≤ 29` → 80.0% de victoria. `peakDebt ≥ 31` → 31.7%.** El diseño quiere
un óptimo interior; hoy hay una esquina obvia en 29.

Tres castigos se apilan exactamente en `BREAK_THRESHOLD = 30` (sección 4.5).
Separar al menos uno:

- Mover el tope del embargo por encima de 30 (o suavizar la rampa) para que
  cruzar el umbral no corte el flujo de oro justo cuando hace falta repagar.
- Que el `collector` forzado **sustituya** un slot en vez de **añadirse** —hoy no
  avanza `slotIndex`, así que es un jefe extra gratis para el juego (−15 a −19pp).
- Compensar la banda alta: el bonus actual (`floor(debt/5)` = +6 de daño a deuda
  30) no paga el triple castigo. O sube el bonus en la banda 30-49, o baja el
  castigo.

**Métrica de aceptación:** que la curva win-rate-vs-peak-debt deje de ser
monótona decreciente y tenga un máximo interior. Hoy `r_pb = −0.25`.

### P3 — Arreglar la escalada de coste del nodo (`1.5^(n-1)` contra oro plano)

Los préstamos son **matemáticamente imposibles** desde el nodo 6 (`+60`, `+91` de
deuda contra un techo de Ejecución de 50), las compras son inasequibles desde el
nodo 4-5, y la regla de thinning de `NodePolicy` (`THIN_NODE = 4`, coste 33) es
**inalcanzable por construcción**. Toda la entrega de C7 es decorativa en la
segunda mitad de la run.

- Bajar `NodeConfig.ESCALATION` de **1.5** a ~**1.15-1.2**, **o** escalar el oro
  de `sequence.json` con la misma curva (hoy es plano: 110 de oro total).
- Verificación rápida tras el cambio: con el logging de P0.2, comprobar que las
  seis prioridades de `NodePolicy` se disparan alguna vez en la segunda mitad.

### P4 — Diversidad de arquetipos: hoy no existe y no se puede medir

El criterio 3 del GDD (≥2 arquetipos distintos en mazos ganadores) **nunca se ha
evaluado**. Con `chooseReward` muerto, las dos políticas construyen el mismo mazo
y el 79.4% de las seeds produce runs bit-idénticas.

- Tras P0.3, volver a correr el sweep y medir `playerArchetype(deck)` del mazo
  final por run. `Archetype.kt` y el `archetypeBiasedOffer()` de `RunManager` ya
  existen: la infraestructura está, solo falta que el simulador la ejercite.
- Añadir una tercera política que juegue explícitamente `LIQUIDITY` (hoy solo hay
  greedy ≈ PRESSURE y leverage ≈ LEVERAGE, e indistinguibles entre sí).

### P5 — Aplazar todo rediseño de cartas hasta P0

**0 de 23 cartas sobreviven a la corrección de Bonferroni.** No hay base
estadística para tocar ninguna carta con este dataset.

- **No resolver la pregunta abierta `bounced_check` vs `foreclosure_express` de
  C8 con estos datos**: +10.0/+8.2pp vs +2.7/+3.2pp, ambos dentro del ruido.
- Cuando P0 aterrice, empezar mirando `mortgage_collateral` (−12.4pp pooled, la
  peor señal, y es la carta de más bloque bruto del pool) y `repo_expert`
  (+11.1pp, la mejor, y es el mejor ataque vainilla). Que las dos cartas **sin
  identidad de economía** copen los extremos es la hipótesis a falsar.
- Dato para vigilar: la tabla de payoff de C4 (`ejecucion` z=−0.33,
  `asset_bubble` z=+0.28, `leverage_strike` z=+0.43, `collateral_hold` z=−1.17)
  es **estadísticamente invisible**. Puede ser que no se drafteen nunca (probable,
  dado 0.2/0.3) o que no rindan en 2 turnos (P1). P0 distingue los dos casos.

### P6 — Higiene (barato, hacer de paso)

- **`docs/GDD.md` dice `MAX_GARNISH_RATE = 0.75`; el código dice `0.6`**
  (`DebtConfig.kt:34`). Corregir el GDD.
- **La línea de la muerte es porosa**: 21/1000 runs superaron
  `EXECUTION_THRESHOLD = 50` y **9 ganaron** (máximo 75). Es consecuencia
  documentada de la Decisión B (el tick de interés no dispara Ejecución), pero
  conviene decidir explícitamente si "Ejecución" debe seguir siendo evitable
  simplemente no jugando cartas que suban deuda.
- El win rate de greedy (54.2%, IC [49.8, 58.6]) está a **0.8pp** del techo de la
  banda. Cualquier cambio de P1-P3 que facilite el juego lo rompe: **re-medir el
  sweep tras cada cambio**, no al final.

---

## Apéndice — Reproducibilidad

- **Dataset:** `app/build/simulation-output/sweep-500.csv` (1001 líneas: cabecera + 1000 filas).
- **Generador:** `app/src/test/java/com/debtsdecks/core/simulation/RunSimulationCsvExportTest.kt`
  (`seedsPerPolicy = 500`, seeds `0..499`, políticas `greedy`/`leverage`).
- **Regenerar:** `./gradlew --no-daemon :app:testDebugUnitTest --tests "*RunSimulationCsvExportTest*"`
- **Constantes usadas en el análisis** (leídas del código, no del GDD):
  `BREAK_THRESHOLD=30`, `EXECUTION_THRESHOLD=50`, `MAX_GARNISH_RATE=0.6`,
  `INTEREST_RATE=0.15`, `LEVERAGE_TARGET=35`, `NodeConfig.ESCALATION=1.5`,
  `HEAL_AMOUNT=8`, `LOAN_DEBT_BASE=8`, `LOAN_GOLD_BASE=12`, `BUY_BASE=8`,
  `REMOVE_BASE=10`, `REPAY_FEE_BASE=3`, `PlayerState.maxHp=50`.
- **Pruebas estadísticas:** McNemar con corrección de continuidad (pareado por
  seed), κ de Cohen, z de dos proporciones, χ² de bondad de ajuste a la uniforme,
  correlación point-biserial, corrección de Bonferroni sobre 23 comparaciones y
  ajuste de no-independencia `z/√2` (justificado por 439/500 logs de oferta
  idénticos entre políticas).
