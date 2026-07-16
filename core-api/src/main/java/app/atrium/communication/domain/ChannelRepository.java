package app.atrium.communication.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    List<Channel> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    Optional<Channel> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Channel> findByCompanyIdAndName(UUID companyId, String name);
}
