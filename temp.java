package com.example.pojo;

import java.time.LocalDate;
import java.util.*;
import java.util.Objects;

public class Person {
    private String id;
    private String firstName;
    private String lastName;
    private LocalDate dob;
    private String email;
    private String phone;
    private String city;
    private boolean active;
    private List<String> tags;
    private Map<String,String> meta;

    public Person() {
        this.tags = new ArrayList<>();
        this.meta = new HashMap<>();
    }

    public Person(String id, String firstName, String lastName, LocalDate dob,
                  String email, String phone, String city, boolean active,
                  List<String> tags, Map<String,String> meta) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dob = dob;
        this.email = email;
        this.phone = phone;
        this.city = city;
        this.active = active;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.meta = meta == null ? new HashMap<>() : new HashMap<>(meta);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<String> getTags() { return Collections.unmodifiableList(tags); }
    public void setTags(List<String> tags) { this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags); }
    public Map<String,String> getMeta() { return Collections.unmodifiableMap(meta); }
    public void setMeta(Map<String,String> meta) { this.meta = meta == null ? new HashMap<>() : new HashMap<>(meta); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return active == person.active &&
                Objects.equals(id, person.id) &&
                Objects.equals(firstName, person.firstName) &&
                Objects.equals(lastName, person.lastName) &&
                Objects.equals(dob, person.dob) &&
                Objects.equals(email, person.email) &&
                Objects.equals(phone, person.phone) &&
                Objects.equals(city, person.city) &&
                Objects.equals(tags, person.tags) &&
                Objects.equals(meta, person.meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, firstName, lastName, dob, email, phone, city, active, tags, meta);
    }

    @Override
    public String toString() {
        return "Person{" +
                "id='" + id + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", dob=" + dob +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", city='" + city + '\'' +
                ", active=" + active +
                ", tags=" + tags +
                ", meta=" + meta +
                '}';
    }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private Person p = new Person();
        public Builder id(String id){p.id=id;return this;}
        public Builder firstName(String fn){p.firstName=fn;return this;}
        public Builder lastName(String ln){p.lastName=ln;return this;}
        public Builder dob(LocalDate d){p.dob=d;return this;}
        public Builder email(String e){p.email=e;return this;}
        public Builder phone(String ph){p.phone=ph;return this;}
        public Builder city(String c){p.city=c;return this;}
        public Builder active(boolean a){p.active=a;return this;}
        public Builder tags(List<String> t){p.tags=t==null?new ArrayList<>():new ArrayList<>(t);return this;}
        public Builder meta(Map<String,String> m){p.meta=m==null?new HashMap<>():new HashMap<>(m);return this;}
        public Person build(){return p;}
    }
}
