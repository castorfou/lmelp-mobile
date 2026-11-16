/* Bouton « Plein écran » sous chaque diagramme mermaid (issue #178).
 *
 * Material dessine les diagrammes dans un shadow DOM fermé : le SVG affiché
 * n'est pas accessible. Le source est donc mémorisé au chargement (avant que
 * Material ne remplace le <pre class="mermaid">), puis redessiné avec
 * window.mermaid dans une surcouche qui couvre l'écran, avec zoom et défilement.
 *
 * Étiquettes de flèches à deux niveaux : |"court@@texte long"|. Le texte long
 * est retiré du source avant que Material ne dessine le diagramme dans la page ;
 * en plein écran, survoler ou toucher l'étiquette courte l'affiche en info-bulle.
 */
(function () {
  const ZOOM_STEP = 1.25;
  const DETAIL_SEPARATOR = "@@";
  let counter = 0;

  function closeOverlay(overlay) {
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(function () {});
    }
    overlay.remove();
    document.body.classList.remove("mermaid-fullscreen-open");
  }

  function makeButton(label, title, onClick) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "mermaid-fullscreen-tool";
    button.textContent = label;
    button.title = title;
    button.addEventListener("click", onClick);
    return button;
  }

  /* Sépare les étiquettes |"court@@long"| : renvoie le source avec les seules
   * étiquettes courtes, et la liste {short, long} dans l'ordre des flèches. */
  function splitLabels(source) {
    const details = [];
    const shortSource = source.replace(/\|"([^"]*)"\|/g, function (match, label) {
      const index = label.indexOf(DETAIL_SEPARATOR);
      if (index < 0) {
        return match;
      }
      const short = label.slice(0, index).trim();
      const long = label.slice(index + DETAIL_SEPARATOR.length).trim();
      details.push({ short: short, long: long.replace(/<br\s*\/?>/g, "\n") });
      return '|"' + short + '"|';
    });
    return { source: shortSource, details: details };
  }

  /* Rattache chaque texte long à son étiquette dessinée. Mermaid dessine les
   * étiquettes dans l'ordre des flèches : à texte court égal (« horaire »
   * deux fois), la première étiquette prend le premier texte long. */
  function attachDetails(overlay, viewport, details) {
    if (details.length === 0) {
      return;
    }
    const tooltip = document.createElement("div");
    tooltip.className = "mermaid-fullscreen-tooltip";
    tooltip.hidden = true;
    overlay.append(tooltip);
    const remaining = details.slice();

    function show(label, text) {
      tooltip.textContent = text;
      tooltip.hidden = false;
      const box = label.getBoundingClientRect();
      const width = tooltip.offsetWidth;
      const left = Math.min(Math.max(box.left + box.width / 2 - width / 2, 8), window.innerWidth - width - 8);
      tooltip.style.left = left + "px";
      const below = box.bottom + 8;
      tooltip.style.top = (below + tooltip.offsetHeight < window.innerHeight
        ? below
        : box.top - tooltip.offsetHeight - 8) + "px";
    }

    viewport.querySelectorAll("g.edgeLabel").forEach(function (label) {
      const text = label.textContent.trim();
      const index = remaining.findIndex(function (detail) { return detail.short === text; });
      if (index < 0) {
        return;
      }
      const long = remaining.splice(index, 1)[0].long;
      label.classList.add("mermaid-fullscreen-has-detail");
      label.addEventListener("mouseenter", function () { show(label, long); });
      label.addEventListener("mouseleave", function () { tooltip.hidden = true; });
      label.addEventListener("click", function (event) {
        event.stopPropagation();
        show(label, long);
      });
    });
    viewport.addEventListener("click", function () { tooltip.hidden = true; });
    viewport.addEventListener("scroll", function () { tooltip.hidden = true; });
  }

  async function openOverlay(source, details) {
    if (!window.mermaid) {
      return;
    }
    const overlay = document.createElement("div");
    overlay.className = "mermaid-fullscreen-overlay";
    const viewport = document.createElement("div");
    viewport.className = "mermaid-fullscreen-viewport";
    const toolbar = document.createElement("div");
    toolbar.className = "mermaid-fullscreen-toolbar";
    overlay.append(toolbar, viewport);
    document.body.append(overlay);
    document.body.classList.add("mermaid-fullscreen-open");

    counter += 1;
    const { svg } = await window.mermaid.render("mermaid-fullscreen-" + counter, source);
    viewport.innerHTML = svg;
    attachDetails(overlay, viewport, details);
    const svgElement = viewport.querySelector("svg");
    const naturalWidth = svgElement.viewBox.baseVal.width || svgElement.getBBox().width;
    svgElement.removeAttribute("height");
    svgElement.style.maxWidth = "none";
    svgElement.style.height = "auto";

    let scale = 1;
    function fitScale() {
      return viewport.clientWidth / naturalWidth;
    }
    function apply() {
      svgElement.style.width = Math.round(naturalWidth * scale) + "px";
    }
    function zoom(factor) {
      scale = Math.min(Math.max(scale * factor, 0.2), 8);
      apply();
    }

    toolbar.append(
      makeButton("−", "Dézoomer", function () { zoom(1 / ZOOM_STEP); }),
      makeButton("+", "Zoomer", function () { zoom(ZOOM_STEP); }),
      makeButton("Ajuster", "Ajuster à la largeur de l'écran", function () {
        scale = fitScale();
        apply();
      }),
      makeButton("✕", "Fermer (Échap)", function () { closeOverlay(overlay); })
    );
    if (details.length > 0) {
      const hint = document.createElement("span");
      hint.className = "mermaid-fullscreen-hint";
      hint.textContent = "Survoler ou toucher une étiquette soulignée pour son détail";
      toolbar.prepend(hint);
    }

    // Par défaut : largeur de l'écran, sans descendre sous la taille réelle du texte.
    scale = Math.max(fitScale(), 1);
    apply();

    if (overlay.requestFullscreen) {
      overlay.requestFullscreen().catch(function () {});
    }
  }

  function setup() {
    document.querySelectorAll("pre.mermaid").forEach(function (pre) {
      if (pre.dataset.fullscreenReady) {
        return;
      }
      pre.dataset.fullscreenReady = "true";
      const split = splitLabels(pre.textContent);
      // Material lit le source plus tard (mermaid se charge en asynchrone) :
      // il ne dessine dans la page que les étiquettes courtes.
      pre.textContent = split.source;
      const button = document.createElement("button");
      button.type = "button";
      button.className = "mermaid-fullscreen-button md-button";
      button.textContent = "⛶ Plein écran";
      button.title = "Afficher le diagramme en plein écran";
      button.addEventListener("click", function () { openOverlay(split.source, split.details); });
      pre.after(button);
    });
  }

  function openedOverlay() {
    return document.querySelector(".mermaid-fullscreen-overlay");
  }

  document.addEventListener("keydown", function (event) {
    const overlay = openedOverlay();
    if (event.key === "Escape" && overlay) {
      closeOverlay(overlay);
    }
  });

  // Sortie du plein écran du navigateur (Échap, geste retour) : fermer aussi la surcouche.
  document.addEventListener("fullscreenchange", function () {
    const overlay = openedOverlay();
    if (!document.fullscreenElement && overlay) {
      closeOverlay(overlay);
    }
  });

  setup();
})();
