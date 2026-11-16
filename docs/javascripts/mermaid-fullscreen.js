/* Schémas mermaid de la doc : disposition ELK, info-bulles, plein écran (issue #178).
 *
 * Material dessinerait les schémas lui-même, avec la disposition dagre et dans
 * un shadow DOM fermé (ni info-bulle, ni plein écran possibles). Le fence
 * mermaid produit donc <pre class="lmelp-mermaid"> (mkdocs.yml), que Material
 * ignore : ce script charge mermaid + ELK et dessine chaque schéma, dans la page
 * comme en plein écran, avec la même disposition.
 *
 * Étiquettes de flèches à deux niveaux : |"court@@texte long"|. Le schéma
 * affiche le texte court ; survoler ou toucher l'étiquette affiche le texte long.
 */
(function () {
  // Versions figées : layout-elk 0.2.x est la branche compatible mermaid 11.
  const MERMAID_URL = "https://cdn.jsdelivr.net/npm/mermaid@11.17.2/dist/mermaid.esm.min.mjs";
  const ELK_URL = "https://cdn.jsdelivr.net/npm/@mermaid-js/layout-elk@0.2.3/dist/mermaid-layout-elk.esm.min.mjs";
  const ZOOM_STEP = 1.25;
  const DETAIL_SEPARATOR = "@@";

  // Styles des flowcharts repris de Material (variables --md-mermaid-*) :
  // mêmes couleurs qu'avant, en clair comme en sombre.
  const THEME_CSS =
    ".node circle,.node ellipse,.node path,.node polygon,.node rect{fill:var(--md-mermaid-node-bg-color);stroke:var(--md-mermaid-node-fg-color)}" +
    "marker{fill:var(--md-mermaid-edge-color)!important}" +
    ".edgeLabel .label rect{fill:#0000}" +
    ".flowchartTitleText{fill:var(--md-mermaid-label-fg-color)}" +
    ".label{color:var(--md-mermaid-label-fg-color);font-family:var(--md-mermaid-font-family)}" +
    ".label foreignObject{line-height:normal;overflow:visible}" +
    ".label div .edgeLabel{color:var(--md-mermaid-label-fg-color)}" +
    ".edgeLabel,.edgeLabel p,.label div .edgeLabel{background-color:var(--md-mermaid-label-bg-color)}" +
    ".edgeLabel,.edgeLabel p{fill:var(--md-mermaid-label-bg-color);color:var(--md-mermaid-edge-color)}" +
    ".edgePath .path,.flowchart-link{stroke:var(--md-mermaid-edge-color)}" +
    ".edgePath .arrowheadPath{fill:var(--md-mermaid-edge-color);stroke:none}" +
    ".cluster rect{fill:var(--md-default-fg-color--lightest);stroke:var(--md-default-fg-color--lighter)}" +
    ".cluster span{color:var(--md-mermaid-label-fg-color);font-family:var(--md-mermaid-font-family)}" +
    "g #flowchart-circleEnd,g #flowchart-circleStart,g #flowchart-crossEnd,g #flowchart-crossStart,g #flowchart-pointEnd,g #flowchart-pointStart{stroke:none}";

  let counter = 0;
  let mermaidLoading = null;

  function loadMermaid() {
    if (!mermaidLoading) {
      mermaidLoading = Promise.all([import(MERMAID_URL), import(ELK_URL)]).then(function (modules) {
        const mermaid = modules[0].default;
        mermaid.registerLayoutLoaders(modules[1].default);
        mermaid.initialize({
          startOnLoad: false,
          layout: "elk",
          theme: "base",
          themeCSS: THEME_CSS,
        });
        return mermaid;
      });
    }
    return mermaidLoading;
  }

  async function render(source, container) {
    const mermaid = await loadMermaid();
    counter += 1;
    const { svg } = await mermaid.render("lmelp-mermaid-" + counter, source);
    container.innerHTML = svg;
    return container.querySelector("svg");
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

  // Une seule info-bulle pour toute la page (position fixe).
  let tooltip = null;

  function hideTooltip() {
    if (tooltip) {
      tooltip.hidden = true;
    }
  }

  function showTooltip(label, text) {
    if (!tooltip) {
      tooltip = document.createElement("div");
      tooltip.className = "lmelp-mermaid-tooltip";
    }
    // En plein écran du navigateur, seul l'élément en plein écran est visible.
    const host = document.fullscreenElement || document.body;
    if (tooltip.parentElement !== host) {
      host.append(tooltip);
    }
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

  /* Rattache chaque texte long à son étiquette dessinée. Mermaid dessine les
   * étiquettes dans l'ordre des flèches : à texte court égal (« horaire »
   * deux fois), la première étiquette prend le premier texte long. */
  function attachDetails(svgElement, details) {
    const remaining = details.slice();
    svgElement.querySelectorAll("g.edgeLabel").forEach(function (label) {
      const text = label.textContent.trim();
      const index = remaining.findIndex(function (detail) { return detail.short === text; });
      if (index < 0) {
        return;
      }
      const long = remaining.splice(index, 1)[0].long;
      label.classList.add("lmelp-mermaid-has-detail");
      label.addEventListener("mouseenter", function () { showTooltip(label, long); });
      label.addEventListener("mouseleave", hideTooltip);
      label.addEventListener("click", function (event) {
        event.stopPropagation();
        showTooltip(label, long);
      });
    });
  }

  function makeButton(label, title, onClick) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "lmelp-mermaid-tool";
    button.textContent = label;
    button.title = title;
    button.addEventListener("click", onClick);
    return button;
  }

  function closeOverlay(overlay) {
    hideTooltip();
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(function () {});
    }
    overlay.remove();
    document.body.classList.remove("lmelp-mermaid-overlay-open");
  }

  async function openOverlay(source, details) {
    const overlay = document.createElement("div");
    overlay.className = "lmelp-mermaid-overlay";
    const viewport = document.createElement("div");
    viewport.className = "lmelp-mermaid-viewport";
    const toolbar = document.createElement("div");
    toolbar.className = "lmelp-mermaid-toolbar";
    overlay.append(toolbar, viewport);
    document.body.append(overlay);
    document.body.classList.add("lmelp-mermaid-overlay-open");
    viewport.addEventListener("scroll", hideTooltip);
    if (overlay.requestFullscreen) {
      overlay.requestFullscreen().catch(function () {});
    }

    const svgElement = await render(source, viewport);
    attachDetails(svgElement, details);
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

    if (details.length > 0) {
      const hint = document.createElement("span");
      hint.className = "lmelp-mermaid-hint";
      hint.textContent = "Survoler ou toucher une étiquette soulignée pour son détail";
      toolbar.append(hint);
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

    // Par défaut : largeur de l'écran, sans descendre sous la taille réelle du texte.
    scale = Math.max(fitScale(), 1);
    apply();
  }

  async function setupDiagram(pre) {
    const split = splitLabels(pre.textContent);
    const container = document.createElement("div");
    container.className = "lmelp-mermaid-diagram";
    try {
      const svgElement = await render(split.source, container);
      attachDetails(svgElement, split.details);
    } catch (error) {
      // CDN injoignable ou erreur de syntaxe : le source reste affiché tel quel.
      console.warn("Schéma mermaid non dessiné :", error);
      return;
    }
    const button = document.createElement("button");
    button.type = "button";
    button.className = "lmelp-mermaid-fullscreen-button md-button";
    button.textContent = "⛶ Plein écran";
    button.title = "Afficher le schéma en plein écran";
    button.addEventListener("click", function () { openOverlay(split.source, split.details); });
    pre.replaceWith(container, button);
  }

  function openedOverlay() {
    return document.querySelector(".lmelp-mermaid-overlay");
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

  document.addEventListener("click", hideTooltip);
  window.addEventListener("scroll", hideTooltip, { passive: true });

  // Un schéma à la fois : mermaid.render n'est pas prévu pour des appels concurrents.
  (async function () {
    for (const pre of document.querySelectorAll("pre.lmelp-mermaid")) {
      await setupDiagram(pre);
    }
  })();
})();
