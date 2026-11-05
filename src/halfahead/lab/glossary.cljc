(ns halfahead.lab.glossary
  "Half Ahead's own words, and what each does when you finish typing it.
  This is product voice, not code: edit freely. Keys are lowercase; the
  lookup also tries a word without a trailing s. Effects come from the
  closed set :ha/word-effect in halfahead.schema; intensity is 0..1.
  Drafted by the foreman on 2026-11-05 for Ben to rewrite.")

(def entries
  {;; the work
   "agent"         {:effect :glow    :intensity 0.6}
   "agents"        {:effect :glow    :intensity 0.7}
   "prompt"        {:effect :pop     :intensity 0.7}
   "context"       {:effect :fuse    :intensity 0.5}
   "token"         {:effect :skitter :intensity 0.4}
   "tokens"        {:effect :skitter :intensity 0.6}
   "eval"          {:effect :quake   :intensity 0.4}
   "evals"         {:effect :quake   :intensity 0.5}
   "harness"       {:effect :fuse    :intensity 0.6}
   "factory"       {:effect :launch  :intensity 0.5}
   "loop"          {:effect :rewind  :intensity 0.5}
   "spec"          {:effect :pop     :intensity 0.5}
   "order"         {:effect :pop     :intensity 0.4}
   "review"        {:effect :glow    :intensity 0.5}
   "diff"          {:effect :skitter :intensity 0.5}
   "test"          {:effect :pop     :intensity 0.5}
   "green"         {:effect :glow    :intensity 0.8}

   ;; things going well
   "ship"          {:effect :launch  :intensity 0.9}
   "shipped"       {:effect :launch  :intensity 1.0}
   "deploy"        {:effect :launch  :intensity 0.9}
   "merge"         {:effect :fuse    :intensity 0.8}
   "merged"        {:effect :fuse    :intensity 0.9}
   "works"         {:effect :wobble  :intensity 0.5}
   "finally"       {:effect :wobble  :intensity 0.8}
   "aha"           {:effect :glow    :intensity 1.0}
   "magic"         {:effect :shimmer :intensity 0.9}
   "wow"           {:effect :wobble  :intensity 1.0}

   ;; things going sideways
   "hallucination" {:effect :shimmer :intensity 1.0}
   "hallucinated"  {:effect :shimmer :intensity 0.9}
   "bug"           {:effect :skitter :intensity 0.9}
   "flaky"         {:effect :jitter  :intensity 0.8}
   "broke"         {:effect :quake   :intensity 0.8}
   "broken"        {:effect :quake   :intensity 0.9}
   "crash"         {:effect :quake   :intensity 1.0}
   "outage"        {:effect :quake   :intensity 1.0}
   "rollback"      {:effect :rewind  :intensity 0.9}
   "revert"        {:effect :rewind  :intensity 0.8}
   "undo"          {:effect :rewind  :intensity 0.6}
   "drift"         {:effect :melt    :intensity 0.6}
   "rot"           {:effect :melt    :intensity 0.7}
   "legacy"        {:effect :melt    :intensity 0.5}
   "sycophancy"    {:effect :shimmer :intensity 0.7}
   "vibes"         {:effect :wobble  :intensity 0.6}

   ;; the support group
   "tired"         {:effect :melt    :intensity 0.5}
   "stuck"         {:effect :jitter  :intensity 0.4}
   "help"          {:effect :glow    :intensity 0.6}
   "thanks"        {:effect :glow    :intensity 0.7}
   "together"      {:effect :fuse    :intensity 0.7}
   "lol"           {:effect :wobble  :intensity 0.7}
   "honestly"      {:effect :pop     :intensity 0.4}

   ;; the community
   "half"          {:effect :pop     :intensity 0.6}
   "ahead"         {:effect :launch  :intensity 0.6}
   "scout"         {:effect :glow    :intensity 0.7}})
