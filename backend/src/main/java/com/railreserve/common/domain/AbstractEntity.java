package com.railreserve.common.domain;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.Hibernate;

/**
 * Base class for all JPA entities.
 *
 * <p>Provides the surrogate primary key and a Hibernate-proxy-safe identity contract.
 * {@code equals()}/{@code hashCode()} are based on the surrogate id, following the
 * well-known pattern:
 * <ul>
 *   <li>{@code hashCode()} is constant per concrete type, so it stays stable even before
 *       the id is assigned (a brand-new, unsaved entity has a {@code null} id);</li>
 *   <li>{@code equals()} treats two entities as equal only once both have a non-null id
 *       and the ids match, and it unwraps Hibernate proxies via {@link Hibernate#getClass}.</li>
 * </ul>
 *
 * <p>A stable business key (e.g. station code or PNR) would be an equally valid basis for
 * identity where one exists; we use the surrogate id uniformly for consistency.
 */
@MappedSuperclass
public abstract class AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        AbstractEntity that = (AbstractEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
