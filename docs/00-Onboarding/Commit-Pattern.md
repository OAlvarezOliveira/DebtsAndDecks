# 📁 Estructura de la Bóveda Obsidian — Debts & Decks (MVP)

> **Patrón de documentación para MVP de 5 minutos** — Diseñado para **búsqueda rápida**, **colaboración satírica** y **integración con commits**.

---


## 🗂️ Estructura de Carpetas (Código + Obsidian)

```
Debts & Decks/
├── docs/                     # Documentación técnica (sincronizada con Obsidian)
│   ├── 00-Onboarding/
│   │   ├── README.md           # Guía de inicio + enlaces a Obsidian
│   │   └── Commit-Pattern.md   # Reglas de commits (este archivo)
│   ├── 01-Game-Design/
│   │   ├── Core-Loop.md        # Loop de 5 minutos
│   │   ├── Cards.md            # Mazo de 10 cartas + textos satíricos
│   │   └── Enemies.md          # 3 combates + jefe final
│   ├── 02-Technical-Design/
│   │   ├── Architecture.md     # Separación Core/GDX
│   │   └── Data-Flow.md        # Flujo de estado (CombatState)
│   └── 03-Satirical-Lore/
│       ├── Corporate-Humor.md  # Chistes corporativos listos para redes
│       └── Error-Messages.md   # Mensajes de derrota/victoria
├── .obsidian/
│   └── workspace.json        # Configuración predefinida para Obsidian
└── vault.obsidian/           # **Bóveda Obsidian principal** (enlazada a docs/)
    ├── 00-Onboarding
    │   ├── [[Guía de Inicio]]
    │   └── [[Patrón de Commits]]
    ├── 01-Game-Design
    │   ├── [[Loop de 5 Minutos]]
    │   ├── [[Mazo de Supervivencia Laboral]]
    │   └── [[Combates de la Oficina]]
    ├── 02-Technical-Design
    │   ├── [[Arquitectura MVP]]
    │   └── [[Flujo de Datos]]
    └── 03-Satirical-Lore
        ├── [[Humor Corporativo]]
        └── [[Mensajes de Derrota/Victoria]]
```

---


## ✍️ Patrón de Documentación (Obsidian)

### **1. Reglas Básicas**
- **Nombres de archivos**: `Título en Español` (ej: `Loop de 5 Minutos.md`).
- **Encabezado obligatorio**:
  ```markdown
  # [[Título]]
  > **Propósito**: ¿Para qué existe este documento? (1 línea)
  > **Última actualización**: YYYY-MM-DD — Por: #734
  ```
- **Tags de Obsidian**:
  - `#mvp` `#game-design` `#technical` `#satire` `#to-review`
  - Ejemplo: `#mvp #game-design #satire`

### **2. Plantilla para Mecánicas** (`01-Game-Design/`)
```markdown
# [[Mazo de Supervivencia Laboral]]
> **Propósito**: Definir las 10 cartas del MVP con humor corporativo.
> **Última actualización**: 2025-08-11 — Por: #734

## 🃏 Cartas del MVP
| **Nombre** | **Coste** | **Efecto** | **Texto Satírico** |
|------------|-----------|------------|---------------------|
| Overtime Request | 1 | +6 Daño | *"¡Trabaja horas extra SIN PAGAR! (El jefe dice que es 'inversión en tu carrera')"* |

## 🎯 Claves del Diseño
- **Simplicidad**: Todas las cartas se resuelven en 1 clic.
- **Humor viral**: Frases como *"Cc: Todos. Bcc: Recursos Humanos"*.

> [!note] ¡Atención!
> Si el jugador no ríe al leer *"Resignation Letter"*, el algoritmo de gamificación capitalista **falla**.
```

### **3. Plantilla para Código** (`02-Technical-Design/`)
```markdown
# [[Arquitectura MVP]]
> **Propósito**: Explicar la separación Core/GDX para el MVP.
> **Última actualización**: 2025-08-11 — Por: #734

```
┌──────────────────┐    ┌──────────────────────────────┐
│   Core (Pure      │    │   GDX Layer (Platform)       │
│   Kotlin)         │    │   - CombatRenderer           │
│                   │    │   - CombatInputHandler       │
│  - CombatEngine   │◀───│                              │
└──────────────────┘    └──────────────────────────────┘
```

## 🔑 Reglas Técnicas
- **Core es agnóstico**: Cero dependencias de LibGDX/Android.
- **Estado inmutable**: `CombatState` como snapshot para renderizar.

> [!tip] ¿Cómo probar?
> Usa `CombatEngineTest` para simular combates sin LibGDX.
```

### **4. Plantilla para Satire** (`03-Satirical-Lore/`)
```markdown
# [[Mensajes de Derrota/Victoria]]
> **Propósito**: Mensajes satíricos que refuerzan el humor negro corporativo.
> **Última actualización**: 2025-08-11 — Por: #734

## 💥 Derrotas
| **Intención** | **Mensaje** |
|---------------|-------------|
| Oferta Renuncia Voluntaria | *"¡Has aceptado la oferta! Derrota: Libertad Laboral (pero sin indemnización)."* |

## 🏆 Victorias
| **Intención** | **Mensaje** |
|---------------|-------------|
| Fase Huelga Virtual | *"¡Has hecho huelga! Victoria: Derrota del Capital (pero tu cuenta corriente colapsó)."* |

> [!quote] Frase Corporativa
> *"Si el jugador no siente vacío al ver 'Victoria: Salario Temporal', el algoritmo de gamificación capitalista ha triunfado."*
```

---


## 📦 Patrón de Commits (Sincronizado con Obsidian)

### **Formato Básico**
```
<tipo>(<ámbito>): <título>

<cuerpo>

<pié>
```

### **Tipos Específicos para Documentación**
| Tipo | Ámbito | Ejemplo | Regla |
|------|--------|---------|-------|
| `docs` | `game` | `docs(game): actualizar mazo de cartas` | Cambios en diseño de juego |
| `docs` | `tech` | `docs(tech): explicar flujo de CombatState` | Cambios técnicos |
| `docs` | `satire` | `docs(satire): añadir mensaje de derrota viral` | Humor corporativo |
| `chore` | `docs` | `chore(docs): sincronizar Obsidian con docs/` | Mantenimiento |

### **Ejemplos Reales**
```
docs(game): actualizar mazo de cartas

- Añadida carta "Resignation Letter" (coste 1, daño 7, agotada)
- Texto satírico: "Atacas y te vas. Daño alto… pero el jefe te llama al día siguiente"
- Meta: Hacer el loop más viral en redes sociales

Closes #12
```

```
docs(satire): añadir mensaje de derrota viral

- Nuevo mensaje para "Oferta Renuncia Voluntaria":
  "¡Has aceptado la oferta! Derrota: Libertad Laboral (pero sin indemnización)."
- Hook: "¡Comparte tu renuncia 'voluntaria' en TikTok! #FalsoAutónomo"

Closes #22
```

```
chore(docs): sincronizar Obsidian con docs/

- Copiados archivos de vault.obsidian/ a docs/
- Actualizados enlaces wikilink a rutas relativas
- Eliminados archivos obsoletos (ADR/0002)
```

---


## 🔗 Integración Obsidian + Git
- **Obsidian → Git**: Cada cambio en `vault.obsidian/` se commitea con `chore(docs): sincronizar Obsidian`.
- **Git → Obsidian**: Al clonar el repo, ejecutar `ln -s docs/ vault.obsidian/` para enlazar carpetas.
- **Pre-commit hook**: Verifica que todos los `[[wikilinks]]` apunten a archivos existentes.

> [!warning] ¡Regla Crítica!
> **Nunca** escribas código en Obsidian. La bóveda es para diseño, no para implementación.

---


## ✅ Checklist de Documentación MVP
- [ ] Todos los documentos tienen `> **Propósito**` y `> **Última actualización**`
- [ ] Uso consistente de `#mvp` y `#satire` en tags de Obsidian
- [ ] Commits de documentación usan `docs(game)`/`docs(satire)`
- [ ] Mensajes de derrota/victoria están en `03-Satirical-Lore/`
- [ ] Enlaces wikilink (`[[Cartas]]`) apuntan a archivos reales

*Última actualización: 2025-08-11 — Por: Departamento de Innovación Ética (DIE)*