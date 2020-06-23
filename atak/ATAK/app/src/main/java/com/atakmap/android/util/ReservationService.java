
package com.atakmap.android.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides simple thread-safe reservation of objects.  Reservation requests are blocked
 * until the requested object is available for reservation.
 *
 * @param <T>   The type of object being reserved.  Compared using equals.
 **/
public class ReservationService<T> {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================

    //==================================
    //  PUBLIC NESTED TYPES
    //==================================

    /**
     * A token used as proof of a reservation.
     *
     * @param <T>       The type of object being reserved.  Compared using
     *                  equals.
     **/
    public static class Reservation<T> {
        protected Reservation(T item) {
            this.item = item;
        }

        private final T item;
    }

    //==================================
    //  PUBLIC METHODS
    //==================================

    /**
     * Checks whether the supplied item is already reserved.
     *
     * @param item    The item to check for reservation.
     * @return        True if the item is reserved; false if not.
     **/
    public boolean isReserved(T item) {
        synchronized (reservations) {
            return reservations.contains(item);
        }
    }

    /**
     * Attempts to reserve the supplied item.  Blocks the calling thread until
     * the item is reserved.
     *
     * @param item    The item to be reserved.
     * @return        A Reservation if the reservation was completed; null if
     *                the thread was interrupted.
     **/
    public Reservation<T> reserve(T item) {
        Reservation<T> reservation = null;

        synchronized (reservations) {
            try {
                while (reservations.contains(item)) {
                    reservations.wait();
                }

                reservations.add(item);
                reservation = new Reservation<>(item);
            } catch (InterruptedException e) {
                reservations.notify();
            } // Notify some other waiter.
        }

        return reservation;
    }

    /**
     * Attempts to reserve the supplied item.  Balks if the item is already
     * reserved.
     *
     * @param item    The item to be reserved.
     * @return        A Reservation if the reservation was completed; null if
     *                the thread was interrupted.
     **/
    public Reservation<T> tryReserve(T item) {
        Reservation<T> reservation = null;

        synchronized (reservations) {
            if (!reservations.contains(item)) {
                reservations.add(item);
                reservation = new Reservation<>(item);
            }
        }

        return reservation;
    }

    /**
     * Runs the supplied Runnable after trying to establishing a reservation for
     * the supplied item.  Balks if the item is already reserved.  Releases the
     * reservation after the Runnable has executed.
     *
     * @param item    The item to be reserved while the Runnable executes.
     * @param r       The Runnable to be executed while the item is reserved.
     * @return        True if the reservation was completed; false if the item
     *                was already reserved.
     **/
    public boolean tryWithReservation(T item,
            Runnable r) {
        Reservation<T> reservation = tryReserve(item);

        if (reservation != null) {
            try {
                r.run();
            } finally {
                unreserve(reservation);
            }
        }

        return reservation != null;
    }

    /**
     * Releases the supplied Reservation, making its contained item available
     * for waiting reservations.
     *
     * @param reservation     The Reservation to be released.
     **/
    public void unreserve(Reservation<T> reservation) {
        if (reservation != null) {
            synchronized (reservations) {
                if (reservations.remove(reservation.item)) {
                    reservations.notify();
                }
            }
        }
    }

    /**
     * Runs the supplied Runnable after establishing a reservation for the
     * supplied item.  Releases the reservation after the Runnable has executed.
     *
     * @param item    The item to be reserved while the Runnable executes.
     * @param r       The Runnable to be executed while the item is reserved.
     * @return        True if the reservation was completed; false if the
     *                thread was interrupted.
     **/
    public boolean withReservation(T item,
            Runnable r) {
        Reservation<T> reservation = reserve(item);

        if (reservation != null) {
            try {
                r.run();
            } finally {
                unreserve(reservation);
            }
        }

        return reservation != null;
    }

    //==================================
    //
    //  PROTECTED INTERFACE
    //
    //==================================

    /**
     * Creates a reservation containing the supplied item.  Derived classes may
     * override this method to perform additional work when a Reservation is
     * created.
     *
     * @param item      The item being reserved.
     * @return          The Reservation token for the item.
     **/
    protected Reservation<T> createReservation(T item) {
        return new Reservation<>(item);
    }

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================

    private final Set<T> reservations = new HashSet<>();
}
