package com.riskboard.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "counterparties")
public class Counterparty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "ricos_code", unique = true, nullable = false)
    private String ricosCode;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String sector;
}
