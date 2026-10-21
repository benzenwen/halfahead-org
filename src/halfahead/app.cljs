(ns halfahead.app
  "The browser shell. A placeholder until the app-shell order replaces it:
  it renders one static line so the build and the page are wired.")

(defn ^:export init! []
  (when-let [root (js/document.getElementById "app")]
    (set! (.-textContent root) "Half Ahead — the conversation starts here.")))
