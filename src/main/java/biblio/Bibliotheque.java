package biblio;

import java.util.HashMap;

/**
 * Класс для примера работы с cucumber
 */
public class Bibliotheque {

	private HashMap<String,Etudiant> etudiants = new HashMap<>();
	private HashMap<String,Livre> livres = new HashMap<>();

	public Etudiant getEtudiantByName(String nom) {
		return etudiants.get(nom);
	}

	public void addEtudiant(Etudiant e) {
		etudiants.put(e.getNom(),e);
	}

	public Livre getLivreByTitle(String titre) {
		return livres.get(titre);
	}

	public void addLivre(Livre l) {
		livres.put(l.getTitre(),l);
	}

}