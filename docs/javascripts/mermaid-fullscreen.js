/* Bouton « Plein écran » sous chaque diagramme mermaid (issue #178).
 *
 * Material dessine les diagrammes dans un shadow DOM fermé : le SVG affiché
 * n'est pas accessible. Le source est donc mémorisé au chargement (avant que
 * Material ne remplace le <pre class="mermaid">), puis redessiné avec
 * window.mermaid dans une surcouche qui couvre l'écran, avec zoom et défilement.
 */
(function () {
  const ZOOM_STEP = 1.25;
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

  async function openOverlay(source) {
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
      const source = pre.textContent;
      const button = document.createElement("button");
      button.type = "button";
      button.className = "mermaid-fullscreen-button md-button";
      button.textContent = "⛶ Plein écran";
      button.title = "Afficher le diagramme en plein écran";
      button.addEventListener("click", function () { openOverlay(source); });
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
