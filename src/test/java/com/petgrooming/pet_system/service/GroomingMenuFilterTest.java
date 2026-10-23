package com.petgrooming.pet_system.service;

import com.petgrooming.pet_system.dto.GroomingItemResponse;
import com.petgrooming.pet_system.dto.PetResponse;
import com.petgrooming.pet_system.enums.CatCoatCategory;
import com.petgrooming.pet_system.enums.CoatType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服務項目菜單依體型篩選（店家規定）：貓咪依毛髮分類、狗狗依體重級距，狗狗未填體重時不顯示體重套餐。
 */
class GroomingMenuFilterTest {

    private final GroomingMenuFilter filter = new GroomingMenuFilter();

    private final GroomingItemResponse nails = item(1L, "DOG", null, null, null);
    private final GroomingItemResponse dogSmall = item(2L, "DOG", "SMALL", null, null);
    private final GroomingItemResponse dogMedium = item(3L, "DOG", "MEDIUM", null, null);
    private final GroomingItemResponse dogSmallLong = item(4L, "DOG", "SMALL", "LONG", null);
    private final GroomingItemResponse catSingle = item(5L, "CAT", null, null, "SINGLE_LAYER");
    private final GroomingItemResponse catLong = item(6L, "CAT", null, null, "LONG_HAIR");
    private final GroomingItemResponse catAddOn = item(7L, "CAT", null, null, null);
    private final List<GroomingItemResponse> menu =
            List.of(nails, dogSmall, dogMedium, dogSmallLong, catSingle, catLong, catAddOn);

    @Test
    void dogSeesOnlyItsWeightTierAndUntieredItems() {
        PetResponse dog = dog(4.0, CoatType.SHORT);

        assertThat(filter.filterForPetShape(menu, true, "DOG", dog)).containsExactly(nails, dogSmall);
        assertThat(filter.needsWeight("DOG", dog)).isFalse();
    }

    @Test
    void dogWithoutWeightSeesNoTieredItemsAndNeedsWeight() {
        for (Double weight : new Double[] {null, 0.0}) {
            PetResponse dog = dog(weight, null);

            assertThat(filter.filterForPetShape(menu, true, "DOG", dog)).containsExactly(nails);
            assertThat(filter.needsWeight("DOG", dog)).isTrue();
        }
    }

    @Test
    void lockedPackageIgnoresWeight() {
        PetResponse dog = dog(null, null);
        dog.setLockedGroomingItemId(dogMedium.getId());

        assertThat(filter.filterForPetShape(menu, true, "DOG", dog)).containsExactly(nails, dogMedium);
        assertThat(filter.needsWeight("DOG", dog)).isFalse();
    }

    @Test
    void catSeesOnlyItsCoatCategory() {
        PetResponse cat = new PetResponse();
        cat.setCatCoatCategory(CatCoatCategory.LONG_HAIR);

        assertThat(filter.filterForPetShape(menu, true, "CAT", cat)).containsExactly(catLong, catAddOn);
        assertThat(filter.needsWeight("CAT", cat)).isFalse();
    }

    @Test
    void catWithoutCoatCategorySeesAllCatItems() {
        assertThat(filter.filterForPetShape(menu, true, "CAT", new PetResponse()))
                .containsExactly(catSingle, catLong, catAddOn);
    }

    @Test
    void noPetMeansNoShapeFilteringAndNoReminder() {
        assertThat(filter.filterForPetShape(menu, true, "DOG", null))
                .containsExactly(nails, dogSmall, dogMedium, dogSmallLong);
        assertThat(filter.needsWeight("DOG", null)).isFalse();
    }

    private static PetResponse dog(Double weight, CoatType coat) {
        PetResponse dog = new PetResponse();
        dog.setWeight(weight);
        dog.setCoatType(coat);
        return dog;
    }

    private static GroomingItemResponse item(Long id, String petType, String weightTier, String coat, String catCoat) {
        GroomingItemResponse item = new GroomingItemResponse();
        item.setId(id);
        item.setApplicablePetType(petType);
        item.setDogWeightTier(weightTier);
        item.setDogCoatLength(coat);
        item.setCatCoatCategory(catCoat);
        return item;
    }
}
