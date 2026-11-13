// Fetches name, address, phone and coordinates of major East Java hospitals (outside Surabaya)
// from the Google Places API (New) Text Search, and writes scripts/hospitals_jatim.json.
//
// Usage (from the repo root):  node scripts/fetch_hospitals_jatim.js
// The API key is read from local.properties (PLACES_API_KEY, or MAPS_API_KEY as fallback).
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
// Key is read from local.properties (gitignored): PLACES_API_KEY if set, otherwise MAPS_API_KEY.
const localProps = fs.readFileSync(path.join(root, "local.properties"), "utf8");
const prop = name => (localProps.match(new RegExp("^" + name + "=(.+)$", "m")) || [])[1]?.trim();
const KEY = prop("PLACES_API_KEY") || prop("MAPS_API_KEY");
if (!KEY) throw new Error("Set PLACES_API_KEY or MAPS_API_KEY in local.properties");

// [query, kota/kabupaten] - RSUD utama tiap kota/kabupaten + RS swasta besar.
const QUERIES = [
  // Malang Raya
  ["RSUD Dr. Saiful Anwar Malang", "Kota Malang"],
  ["Rumah Sakit Lavalette Malang", "Kota Malang"],
  ["RS Panti Nirmala Malang", "Kota Malang"],
  ["RS Panti Waluya Sawahan Malang", "Kota Malang"],
  ["Rumah Sakit Universitas Brawijaya Malang", "Kota Malang"],
  ["RSI Unisma Malang", "Kota Malang"],
  ["RS Persada Hospital Malang", "Kota Malang"],
  ["RSI Aisyiyah Malang", "Kota Malang"],
  ["RS Hermina Tangkubanprahu Malang", "Kota Malang"],
  ["RSUD Kanjuruhan Kepanjen Malang", "Kabupaten Malang"],
  ["RS Wava Husada Kepanjen", "Kabupaten Malang"],
  ["RS Baptis Batu", "Kota Batu"],
  ["RSUD Karsa Husada Batu", "Kota Batu"],
  // Sidoarjo
  ["RSUD R.T. Notopuro Sidoarjo", "Kabupaten Sidoarjo"],
  ["RSI Siti Hajar Sidoarjo", "Kabupaten Sidoarjo"],
  ["RS Delta Surya Sidoarjo", "Kabupaten Sidoarjo"],
  ["RS Mitra Keluarga Waru Sidoarjo", "Kabupaten Sidoarjo"],
  ["RSUD Sidoarjo Barat Krian", "Kabupaten Sidoarjo"],
  // Gresik
  ["RSUD Ibnu Sina Gresik", "Kabupaten Gresik"],
  ["RS Semen Gresik", "Kabupaten Gresik"],
  ["RS Petrokimia Gresik", "Kabupaten Gresik"],
  // Mojokerto & Jombang
  ["RSUD dr. Wahidin Sudiro Husodo Kota Mojokerto", "Kota Mojokerto"],
  ["RSUD Prof. dr. Soekandar Mojosari", "Kabupaten Mojokerto"],
  ["RSUD Jombang", "Kabupaten Jombang"],
  // Kediri, Blitar, Tulungagung, Trenggalek, Nganjuk
  ["RSUD Gambiran Kota Kediri", "Kota Kediri"],
  ["RS Baptis Kediri", "Kota Kediri"],
  ["RSUD Simpang Lima Gumul Kediri", "Kabupaten Kediri"],
  ["RSUD Mardi Waluyo Kota Blitar", "Kota Blitar"],
  ["RSUD Ngudi Waluyo Wlingi Blitar", "Kabupaten Blitar"],
  ["RSUD dr. Iskak Tulungagung", "Kabupaten Tulungagung"],
  ["RSUD dr. Soedomo Trenggalek", "Kabupaten Trenggalek"],
  ["RSUD Nganjuk", "Kabupaten Nganjuk"],
  // Madiun Raya
  ["RSUD dr. Soedono Madiun", "Kota Madiun"],
  ["RSUD Kota Madiun Sogaten", "Kota Madiun"],
  ["RSUD dr. Harjono Ponorogo", "Kabupaten Ponorogo"],
  ["RSUD dr. Soeroto Ngawi", "Kabupaten Ngawi"],
  ["RSUD dr. Sayidiman Magetan", "Kabupaten Magetan"],
  ["RSUD dr. Darsono Pacitan", "Kabupaten Pacitan"],
  // Pantura barat
  ["RSUD dr. R. Sosodoro Djatikoesoemo Bojonegoro", "Kabupaten Bojonegoro"],
  ["RSUD dr. R. Koesma Tuban", "Kabupaten Tuban"],
  ["RSUD dr. Soegiri Lamongan", "Kabupaten Lamongan"],
  ["RS Muhammadiyah Lamongan", "Kabupaten Lamongan"],
  // Tapal kuda
  ["RSUD Bangil Pasuruan", "Kabupaten Pasuruan"],
  ["RSUD dr. R. Soedarsono Kota Pasuruan", "Kota Pasuruan"],
  ["RSUD dr. Mohamad Saleh Kota Probolinggo", "Kota Probolinggo"],
  ["RSUD Waluyo Jati Kraksaan Probolinggo", "Kabupaten Probolinggo"],
  ["RSUD dr. Haryoto Lumajang", "Kabupaten Lumajang"],
  ["RSD dr. Soebandi Jember", "Kabupaten Jember"],
  ["RS Citra Husada Jember", "Kabupaten Jember"],
  ["RSUD dr. H. Koesnadi Bondowoso", "Kabupaten Bondowoso"],
  ["RSUD dr. Abdoer Rahem Situbondo", "Kabupaten Situbondo"],
  ["RSUD Blambangan Banyuwangi", "Kabupaten Banyuwangi"],
  ["RSUD Genteng Banyuwangi", "Kabupaten Banyuwangi"],
  // Madura
  ["RSUD Syarifah Ambami Rato Ebu Bangkalan", "Kabupaten Bangkalan"],
  ["RSUD dr. Mohammad Zyn Sampang", "Kabupaten Sampang"],
  ["RSUD dr. H. Slamet Martodirdjo Pamekasan", "Kabupaten Pamekasan"],
  ["RSUD dr. H. Moh. Anwar Sumenep", "Kabupaten Sumenep"],
];

// Rough bounding box of East Java (incl. Madura) to bias & sanity-check results.
const JATIM = { low: { latitude: -8.8, longitude: 110.9 }, high: { latitude: -6.7, longitude: 114.7 } };

async function search(query) {
  const res = await fetch("https://places.googleapis.com/v1/places:searchText", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": KEY,
      "X-Goog-FieldMask": "places.id,places.displayName,places.formattedAddress,places.location,places.nationalPhoneNumber,places.businessStatus,places.types",
    },
    body: JSON.stringify({ textQuery: query, languageCode: "id", maxResultCount: 1, locationRestriction: { rectangle: JATIM } }),
  });
  const json = await res.json();
  if (!res.ok) throw new Error(`${query}: ${JSON.stringify(json)}`);
  return (json.places || [])[0];
}

(async () => {
  const out = [];
  for (const [query, area] of QUERIES) {
    const p = await search(query);
    if (!p) { out.push({ query, area, error: "no result" }); continue; }
    out.push({
      query,
      area,
      placeId: p.id,
      nama: p.displayName?.text || "",
      alamat: (p.formattedAddress || "").replace(/, Indonesia$/, ""),
      telepon: p.nationalPhoneNumber || "",
      latitude: p.location?.latitude,
      longitude: p.location?.longitude,
      businessStatus: p.businessStatus || "",
      isHospital: (p.types || []).includes("hospital"),
    });
  }
  fs.writeFileSync(path.join(__dirname, "hospitals_jatim.json"), JSON.stringify(out, null, 2));
  console.log(`saved ${out.length} entries`);
})();
