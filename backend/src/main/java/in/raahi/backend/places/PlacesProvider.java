package in.raahi.backend.places;

import java.util.List;

public interface PlacesProvider {
    record Place(String id, String name, double lat, double lng, Double distanceKm) {}

    /** Returns nearby places of the given Raahi place-type ("dhaba","parking","toilet",
     * "atm","fuel","hotel"), or throws PlacesUnavailableException if the provider can't be
     * reached — callers must surface that honestly, never fall back to invented places. */
    List<Place> nearby(String placeType, double lat, double lng, double radiusKm) throws PlacesUnavailableException;

    class PlacesUnavailableException extends Exception {
        public PlacesUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
