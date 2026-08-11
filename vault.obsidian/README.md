# 📁 Bóveda Obsidian — Debts & Decks (MVP)

> **Documentación satírica para un MVP de 5 minutos** — Diseñada para **búsqueda rápida**, **viralidad laboral** y **integración con commits**.

---


## 🌐 Mapa de la Bóveda

### **00-Onboarding**
- [[Guía de Inicio]] — Cómo navegar esta bóveda
- [[Patrón de Commits]] — Reglas para documentar cambios

### **01-Game-Design**
- [[Loop de 5 Minutos]] — El núcleo del MVP
- [[Mazo de Supervivencia Laboral]] — 10 cartas satíricas
- [[Combates de la Oficina]] — 3 enemigos + jefe final
- [[Intenciones de Derrota/Victoria]] — Mensajes virales

### **02-Technical-Design**
- [[Arquitectura MVP]] — Separación Core/GDX
- [[Flujo de CombatState]] — Estado inmutable
- [[Input Handler Satírico]] — Cómo el jugador interactúa

### **03-Satirical-Lore**
- [[Humor Corporativo]] — Reglas del chiste capitalista
- [[Mensajes de Error Éticos]] — "Error 404: Tu vida personal no existe"
- [[Easter Eggs Laborales]] — "¡Contratamos un bot para tu puesto!"

---


## 📝 Plantilla Universal (TODOS los documentos)

```markdown
# [[Título]]
> **Propósito**: ¿Para qué existe este documento? (1 línea)
> **Última actualización**: YYYY-MM-DD — Por: #734
> **Tags**: #mvp #game-design #satire

[Contenido aquí]

> [!note] ¡Regla Crítica!
> **Nunca** escribas código en Obsidian. La bóveda es para diseño, no para implementación.
```

---


## 🃏 Ejemplo: [[Mazo de Supervivencia Laboral]]

```markdown
# [[Mazo de Supervivencia Laboral]]
> **Propósito**: Definir las 10 cartas del MVP con humor corporativo.
> **Última actualización**: 2025-08-11 — Por: #734
> **Tags**: #mvp #game-design #satire

## 🗂️ Cartas del MVP
| **Nombre** | **Coste** | **Efecto** | **Texto Satírico** |
|------------|-----------|------------|---------------------|
| **Overtime Request** | 1 | +6 Daño | *"¡Trabaja horas extra SIN PAGAR! (El jefe dice que es 'inversión en tu carrera')"* |
| **Sick Day Excuse** | 1 | +5 Bloqueo | *"Te 'enfermas' para no ir a la reunión. Ganas 5 bloqueo… pero el DRH te sigue vigilando."* |

## 🎯 Claves del Diseño
- **Simplicidad brutal**: Todas las cartas se resuelven en 1 clic.
- **Humor viral**: Frases como *"Cc: Todos. Bcc: Recursos Humanos"*.

> [!quote] Frase Corporativa
> *"Si el jugador no ríe al leer 'Resignation Letter', el algoritmo de gamificación capitalista falla."*
```

---


## ⚙️ Ejemplo: [[Arquitectura MVP]]

```markdown
# [[Arquitectura MVP]]
> **Propósito**: Explicar la separación Core/GDX para el MVP.
> **Última actualización**: 2025-08-11 — Por: #734
> **Tags**: #mvp #technical #core

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

---


## 💥 Ejemplo: [[Intenciones de Derrota/Victoria]]

```markdown
# [[Intenciones de Derrota/Victoria]]
> **Propósito**: Mensajes satíricos que refuerzan el humor negro corporativo.
> **Última actualización**: 2025-08-11 — Por: #734
> **Tags**: #mvp #satire #game-design

## 💀 Derrotas
| **Intención** | **Mensaje** | **Hook Viral** |
|---------------|-------------|----------------|
| **Oferta Renuncia Voluntaria** | *"¡Has aceptado la oferta! Derrota: Libertad Laboral (pero sin indemnización)."* | *"¡Comparte tu renuncia 'voluntaria' en TikTok! #FalsoAutónomo"* |

## 🏆 Victorias
| **Intención** | **Mensaje** | **Hook Viral** |
|---------------|-------------|----------------|
| **Fase Huelga Virtual** | *"¡Has hecho huelga! Victoria: Derrota del Capital (pero tu cuenta corriente colapsó)."* | *"¡Descarga el certificado de huelga exitosa! (No válido ante la ley)"* |

> [!warning] Advertencia del DIE
> *"Si el jugador no siente vacío al ver 'Victoria: Salario Temporal', el algoritmo de gamificación capitalista ha triunfado."*
```

---


## 🔄 Integración con Git

### **Flujo de Trabajo**
1. **Editar en Obsidian**: Cambios en `vault.obsidian/`
2. **Sincronizar con docs/**: Ejecutar `chore(docs): sincronizar Obsidian` (ver [[Patrón de Commits]])
3. **Commitear cambios**:
   ```
docs(game): actualizar mazo de cartas

- Añadida carta "Resignation Letter" (coste 1, daño 7, agotada)
- Texto satírico: "Atacas y te vas. Daño alto… pero el jefe te llama al día siguiente"
- Meta: Hacer el loop más viral en redes sociales

Closes #12
   ```

### **Pre-commit Hook**
```bash
# Verifica que todos los wikilinks apunten a archivos existentes
grep -r "\[\[" vault.obsidian/ | awk -F'[[|]]' '{print $2}' | xargs -I{} find vault.obsidian/ -name "{}.md" || exit 1
```

---


## ✅ Checklist MVP Obsidian
- [x] Todos los documentos tienen `> **Propósito**` y `> **Última actualización**`
- [x] Uso consistente de `#mvp` y `#satire` en tags
- [x] Plantillas aplicadas en Game Design, Technical Design y Satirical Lore
- [ ] Mensajes de error en [[Mensajes de Error Éticos]] completos
- [ ] Enlaces wikilink (`[[Cartas]]`) verificados con pre-commit hook

*Última actualización: 2025-08-11 — Por: Departamento de Innovación Ética (DIE)*