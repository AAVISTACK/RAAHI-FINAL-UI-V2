package in.raahi.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "daily_tips")
public class DailyTip {

    @Id
    private Integer id;

    private String title;

    @Lob
    private String body;

    public Integer getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
}
