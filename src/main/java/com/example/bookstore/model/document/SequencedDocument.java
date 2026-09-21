package com.example.bookstore.model.document;

/**
 * Document dung khoa chinh kieu Long (giu tuong thich voi SQL Server cu).
 *
 * <p>LY DO: MongoDB khong tu sinh id cho kieu Long/ObjectId tu dong nhu IDENTITY
 * cua SQL Server, nen ta cap id bang collection {@code counters}
 * (findAndModify + $inc - atomic). Nho interface nay + MongoIdAssignmentCallback,
 * cac service van chi goi {@code repository.save(entity)} nhu cu.</p>
 *
 * <p>DANH DOI (ghi trong bao cao): auto-increment la anti-pattern trong MongoDB
 * vi tao diem nong ghi (contention) tren 1 document counter. Do an chon giu
 * Long id de KHONG pha vo 68 endpoint + 21 file JS + 57 DTO hien co.</p>
 */
public interface SequencedDocument {

    Long getId();

    void setId(Long id);

    /** Ten counter (thuong trung ten collection) dung de cap id. */
    default String sequenceName() {
        return null; // null => dung ten collection (xem MongoIdAssignmentCallback)
    }
}
