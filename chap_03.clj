; use namespaces
(clojure/in-ns 'cddb)
(clojure/refer 'clojure)
(clojure.contrib.lib/use '(clojure.contrib test-is))
(clojure.contrib.lib/use '(clojure.contrib string))
(clojure.contrib.lib/use '(clojure.contrib duck-streams))

; struct instead of plist
(defstruct cd :title :artist :rating :ripped)

; pass db (not mutable data)
(defn add-records [db & cd] (concat db cd))

(defn init-db []
  (add-records '() 
	      (struct cd "Roses" "Kathy Mattea" 7 true)
	      (struct cd "Fly" "Dixie Chicks" 8 true)
	      (struct cd "Home" "Dixie Chicks" 9 true)))

; use two doseqs (String/format not as flexible as CL format)
; better way than wrapping cd in (seq cd)?
(defn dump-db [db]
  (doseq cd db
    (doseq [key value] (seq cd) 
      (print (format "%10s: %s\n" (name key) value)))
    (println)))

; use Java interop + exception handling instead of parse-integer plus junk option
(defn parse-integer [str]
  (try (Integer/parseInt str) 
       (catch NumberFormatException nfe 0)))

(defn prompt-read [prompt]
  (print (format "%s: " prompt))
  (flush)
  (read-line))

; handwritten instead of built-in
(defn y-or-n-p [prompt]
  (= "y"
     (loop []
       (or 
	(re-matches #"[yn]" (.toLowerCase (prompt-read prompt)))
	(recur)))))

(defn prompt-for-cd []
  (struct 
   cd 
   (prompt-read "Title")
   (prompt-read "Artist")
   (parse-integer (prompt-read "Rating"))
   (y-or-n-p "Ripped [y/n]")))

(defn add-cds [db]
  (lazy-cat 
   db
   (loop [prev-cds '()]
     (let [cds (cons (prompt-for-cd) prev-cds)]
       (if (not (y-or-n-p "Another? [y/n]"))
	 cds
	 (recur cds))))))

; probably a simpler, more efficient approach	 
(defn save-db [db filename]
  (spit 
   filename 
   (with-out-str (print db))))

(defn load-db [filename] 
  (with-in-str (slurp filename)
    (read)))

; fn literal instead of lambda
; :artist key in function position
(defn artist-selector [artist]
  #(= (:artist %) artist))

; usage for artist-selector
; (filter (cddb/artist-selector "Dixie Chicks") (cddb/init-db))

; more general (but allows "bad" keys to be specified)
(defn where [criteria]
  (fn [cd]
    (loop [criteria criteria] 
      (let [[k,v] (first criteria)]
	(or (not k)
	    (and (= (k cd) v) (recur (rest criteria))))))))

; in Clojure it is idiomatic for the functional argument to come first
(defn update [criteria db updates]
  (map 
   (fn [cd] 
     (if (criteria cd)
       (merge cd updates)
       cd))
   db))
  
(defmacro backwards [expr] (reverse expr))

; create a var for make-comparison-expr to use (should make this private)
(def where-cd nil)

; destructuring in anticipation of make-comparisons-list
(defn make-comparison-expr [[field value]]
  `(= (~field where-cd) ~value))

(defn make-comparisons-list [criteria]
  (map make-comparison-expr criteria))

(defmacro where [criteria]
  `(fn [cd#]
     (binding [where-cd cd#]
       (and ~@(make-comparisons-list criteria)))))

