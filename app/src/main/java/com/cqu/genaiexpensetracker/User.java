/**
 * User.java
 * -------------------------
 * Author: Kapil Pandey
 * Syndey Group
 *
 * Description:
 * This is the model class used for storing and retrieving user data
 * in Firebase Firestore. It represents a user with:
 * - Unique identifier (UID)
 * - Full name
 * - Email address
 *
 * Features:
 * - Default constructor required for Firebase deserialization
 * - Parameterized constructor for quick object creation
 * - Getters and setters for all fields
 */

package com.cqu.genaiexpensetracker;

public class User {

    // Fields stored in Firestore
    private String uid;
    private String name;
    private String email;

    /**
     * Required no-arg constructor for Firestore deserialization.
     */
    public User() {}

    /**
     * Constructs a User object with all fields.
     *
     * @param name  Full name of the user
     * @param email Email address of the user
     * @param uid   Firebase UID of the user
     */
    public User(String name, String email, String uid) {
        this.name = name;
        this.email = email;
        this.uid = uid;
    }

    /**
     * Returns the UID of the user.
     * @return UID as String
     */
    public String getUid() {
        return uid;
    }

    /**
     * Sets the UID of the user.
     * @param uid Firebase UID
     */
    public void setUid(String uid) {
        this.uid = uid;
    }

    /**
     * Returns the name of the user.
     * @return Name as String
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the user.
     * @param name Full name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the user's email address.
     * @return Email as String
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the user's email address.
     * @param email Email address
     */
    public void setEmail(String email) {
        this.email = email;
    }
}
