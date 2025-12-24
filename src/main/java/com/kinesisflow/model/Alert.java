package com.kinesisflow.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@NoArgsConstructor
public class Alert {

    @EmbeddedId
    private AlertId id;

    @ManyToMany(cascade = {CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH})
    @JoinTable(
            name = "alert_user",
            joinColumns = {
                    @JoinColumn(name = "price", referencedColumnName = "price"),
                    @JoinColumn(name = "asset", referencedColumnName = "asset"),
                    @JoinColumn(name = "comparison_type", referencedColumnName = "comparisonType")
            },
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> users = new ArrayList<>();

    @Version
    private int version;

    public Alert(BigDecimal price, String asset, int comparisonType) {
        this.id = new AlertId(price, asset, comparisonType);
        this.users = new ArrayList<>();
    }
    public boolean addUser(User user) {
        if (this.users.contains(user)) {
            return false;
        }
        this.users.add(user);


        if (!user.getAlerts().contains(this)) {
            user.getAlerts().add(this);
        }
        return true;
    }
    public void removeUser(User user) {
        this.users.remove(user);
        user.getAlerts().remove(this);
    }
}
