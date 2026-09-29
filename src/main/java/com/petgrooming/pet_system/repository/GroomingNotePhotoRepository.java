package com.petgrooming.pet_system.repository;

import com.petgrooming.pet_system.model.GroomingNotePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface GroomingNotePhotoRepository extends JpaRepository<GroomingNotePhoto, Long> {

    List<GroomingNotePhoto> findByNoteIdOrderBySortOrderAscIdAsc(Long noteId);

    List<GroomingNotePhoto> findByNoteIdInOrderBySortOrderAscIdAsc(Collection<Long> noteIds);
}
