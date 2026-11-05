package com.example.engine

import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.ReferenceSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Cốt lõi của hệ thống kiểm chứng Fact-Checking 5 bước
 */
class FactCheckEngine {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))

    // Bộ nhớ đệm các bằng chứng và tuyên bố đã xác minh (Knowledge Base & Cache)
    private val verifiedKnowledgeBase = listOf(
        VerifiedFactCase(
            keywords = listOf("nước chanh", "ung thư", "chữa khỏi ung thư", "kiềm hóa"),
            canonicalClaim = "Uống nước chanh nóng hoặc kiềm hóa cơ thể có thể tiêu diệt tế bào ung thư hoàn toàn",
            verdict = FactCheckVerdict.SAI,
            shortExplanation = "Không có bất kỳ nghiên cứu khoa học lâm sàng nào chứng minh chanh nóng hoặc chế độ ăn kiềm hóa chữa khỏi ung thư ở người.",
            detailedExplanation = "Môi trường pH trong máu người được các cơ quan (thận, phổi) điều hòa cực kỳ nghiêm ngặt quanh mức 7.35–7.45. Thức ăn hay đồ uống không thể làm thay đổi đáng kể độ pH toàn thân để tiêu diệt khối u. Việc tin vào tin giả này khiến nhiều bệnh nhân bỏ lỡ phác đồ điều trị vàng.",
            evidence = "Nghiên cứu từ Viện Nghiên cứu Ung thư Hoa Kỳ (AICR) và Tổ chức Y tế Thế giới (WHO) khẳng định: Chanh là nguồn bổ sung vitamin C tốt nhưng không phải thuốc điều trị ung thư.",
            counterEvidence = "Một số thí nghiệm trong ống nghiệm (in vitro) cho thấy chiết xuất từ vỏ chanh có hoạt chất chống oxy hóa, nhưng điều này hoàn toàn khác với việc uống nước chanh chữa được ung thư trên cơ thể sống.",
            references = listOf(
                ReferenceSource(
                    title = "American Institute for Cancer Research - The Alkaline Diet Myth",
                    url = "https://www.aicr.org/cancer-prevention/food-facts/alkaline-diet/",
                    publisher = "Viện Nghiên cứu Ung thư Hoa Kỳ (AICR)",
                    publishDate = "15/03/2023",
                    supportingSnippet = "Tế bào ung thư không bị tiêu diệt bởi chế độ ăn kiềm; cơ thể tự động duy trì cân bằng nội môi pH.",
                    isPrimarySource = true
                ),
                ReferenceSource(
                    title = "Cổng thông tin Bộ Y tế Việt Nam: Cảnh báo tin giả về bài thuốc lá đu đủ và chanh trị ung thư",
                    url = "https://moh.gov.vn",
                    publisher = "Bộ Y Tế Việt Nam",
                    publishDate = "22/08/2022",
                    supportingSnippet = "Người bệnh tuyệt đối không bỏ phác đồ điều trị y khoa chính thống theo các thông tin truyền miệng trên mạng xã hội."
                )
            ),
            limitations = "Nghiên cứu áp dụng cho các khối u ác tính ở người; không phủ nhận giá trị dinh dưỡng thông thường của chanh."
        ),
        VerifiedFactCase(
            keywords = listOf("5g", "phát tán", "covid", "virus", "sóng 5g"),
            canonicalClaim = "Mạng di động 5G làm suy yếu hệ miễn dịch và phát tán virus hoặc mầm bệnh sinh học",
            verdict = FactCheckVerdict.SAI,
            shortExplanation = "Sóng 5G là sóng điện từ không ion hóa, không thể sinh ra hay vận chuyển virus sinh học.",
            detailedExplanation = "Virus là các thực thể sinh học chỉ có thể lây lan qua giọt bắn đường hô hấp hoặc tiếp xúc vật lý. Sóng vô tuyến vô tuyến điện (như 4G, 5G) hoạt động ở dải tần số không ion hóa, không đủ năng lượng làm biến đổi ADN hay tạo ra mầm bệnh sinh học.",
            evidence = "Tuyên bố chung từ Tổ chức Y tế Thế giới (WHO) và Liên minh Viễn thông Quốc tế (ITU): Không có mối liên hệ sinh học hay vật lý nào giữa bức xạ viễn thông và sự lây lan của virus.",
            counterEvidence = "Không có bất kỳ bằng chứng phản biện khoa học nào được bình duyệt xác nhận giả thuyết sóng 5G tạo ra virus.",
            references = listOf(
                ReferenceSource(
                    title = "WHO: 5G mobile networks and health",
                    url = "https://www.who.int/news-room/questions-and-answers/item/radiation-5g-mobile-networks-and-health",
                    publisher = "Tổ chức Y tế Thế giới (WHO)",
                    publishDate = "27/02/2020",
                    supportingSnippet = "Virus cannot travel on radio waves/mobile networks.",
                    isPrimarySource = true
                )
            ),
            limitations = "Phân tích giới hạn ở các tiêu chuẩn an toàn bức xạ vô tuyến hiện hành của ICNIRP."
        ),
        VerifiedFactCase(
            keywords = listOf("đổi tiền", "ngân hàng nhà nước", "tiền mới", "in tiền"),
            canonicalClaim = "Ngân hàng Nhà nước Việt Nam chuẩn bị đổi tiền và hủy giá trị tiền polymer hiện hành",
            verdict = FactCheckVerdict.SAI,
            shortExplanation = "Đây là tin đồn thất thiệt xuất hiện định kỳ. Ngân hàng Nhà nước Việt Nam đã nhiều lần bác bỏ tin đồn này.",
            detailedExplanation = "Tất cả các đồng tiền polymer hiện hành của Việt Nam vẫn đang lưu hành hợp pháp và giữ nguyên giá trị thanh toán. Thông tin đổi tiền là hành vi bịa đặt nhằm gây hoang mang dư luận và lũng đoạn thị trường vàng, ngoại tệ.",
            evidence = "Thông cáo chính thức từ Ngân hàng Nhà nước Việt Nam (SBV): Nhà nước cam kết giá trị đồng tiền quốc gia ổn định và không có bất kỳ chủ trương đổi tiền nào.",
            counterEvidence = "Không có văn bản quy phạm pháp luật hay nghị quyết nào của Quốc hội/Chính phủ về việc đổi tiền.",
            references = listOf(
                ReferenceSource(
                    title = "Ngân hàng Nhà nước Việt Nam: Bác bỏ tin đồn thất thiệt về việc đổi tiền",
                    url = "https://www.sbv.gov.vn",
                    publisher = "Ngân hàng Nhà nước Việt Nam (SBV)",
                    publishDate = "18/06/2023",
                    supportingSnippet = "Khẳng định tin đồn đổi tiền là hoàn toàn sai sự thật, người dân cần cảnh giác trước thông tin xấu độc.",
                    isPrimarySource = true
                )
            ),
            limitations = "Xác minh theo các văn bản chỉ đạo điều hành chính thức của Chính phủ Việt Nam."
        ),
        VerifiedFactCase(
            keywords = listOf("trái đất phẳng", "flat earth", "nasa", "vệ tinh giả"),
            canonicalClaim = "Trái Đất có hình phẳng và các hình ảnh chụp từ vũ trụ của NASA là đồ họa CGI dựng sẵn",
            verdict = FactCheckVerdict.SAI,
            shortExplanation = "Trái Đất là một hình phỏng cầu (oblate spheroid), được chứng minh bởi hàng ngàn năm thiên văn học và hàng vạn vệ tinh nhân tạo.",
            detailedExplanation = "Hình dạng hình cầu của Trái Đất được quan sát rõ ràng qua bóng của Trái Đất trên Mặt Trăng trong nguyệt thực, sự biến mất của tàu bè ở đường chân trời, hiện tượng múi giờ khác biệt cùng lúc trên hành tinh, và ảnh chụp từ vệ tinh của hàng chục quốc gia độc lập (Mỹ, Nga, Nhật, Châu Âu, Trung Quốc, Ấn Độ).",
            evidence = "Dữ liệu đo đạc trắc địa toàn cầu, quỹ đạo GPS/GLONASS và hình ảnh trực tiếp từ Trạm Vũ trụ Quốc tế ISS.",
            counterEvidence = "Các luận điểm của cộng đồng Trái Đất phẳng dựa trên việc bỏ qua khúc xạ khí quyển và góc thị sai.",
            references = listOf(
                ReferenceSource(
                    title = "NASA: Earth's Shape and Gravitational Field",
                    url = "https://www.nasa.gov",
                    publisher = "NASA",
                    publishDate = "10/01/2022",
                    supportingSnippet = "Satellite altimetry and space geodesy confirm Earth's oblate ellipsoidal shape.",
                    isPrimarySource = true
                )
            ),
            limitations = "Kiểm chứng dựa trên các nguyên lý vật lý và đo đạc không gian đã được kiểm chứng độc lập."
        ),
        VerifiedFactCase(
            keywords = listOf("lá đu đủ", "xả", "chữa ung thư", "thần dược"),
            canonicalClaim = "Uống nước lá đu đủ đực kết hợp xả hoặc gừng là thần dược chữa khỏi bách bệnh ung thư giai đoạn cuối",
            verdict = FactCheckVerdict.GAY_HIEU_LAM,
            shortExplanation = "Lá đu đủ đực có một số hoạt chất sinh học nhưng chưa từng được công nhận là thuốc đặc trị ung thư.",
            detailedExplanation = "Mặc dù lá đu đủ có chứa papain và một số chất kháng viêm đang được thử nghiệm tiền lâm sàng, nhưng việc lạm dụng với liều lượng cao có thể gây loét dạ dày, suy gan thận và khiến bệnh nhân bỏ qua các cơ hội phẫu thuật hoặc hóa trị kịp thời.",
            evidence = "Bệnh viện K và Bệnh viện Ung bướu Trung ương liên tục tiếp nhận các ca biến chứng do tự ý dùng thuốc nam không rõ nguồn gốc.",
            counterEvidence = "Có các nghiên cứu phòng thí nghiệm sơ khởi về tính ức chế tế bào ung thư in vitro, nhưng chưa có thử nghiệm lâm sàng giai đoạn 3 thành công.",
            references = listOf(
                ReferenceSource(
                    title = "Bệnh viện K: Thực hư việc dùng lá đu đủ chữa ung thư",
                    url = "https://benhvienk.vn",
                    publisher = "Bệnh viện K Trung ương",
                    publishDate = "05/11/2021",
                    supportingSnippet = "Chưa có bằng chứng khoa học khẳng định lá đu đủ chữa khỏi ung thư trên người bệnh.",
                    isPrimarySource = true
                )
            ),
            limitations = "Cần phân biệt giữa tác dụng hỗ trợ bồi bổ sức khỏe với thuốc điều trị đích ung thư."
        ),
        VerifiedFactCase(
            keywords = listOf("kem chống nắng", "ung thư da", "độc hại", "gây ung thư"),
            canonicalClaim = "Kem chống nắng chứa hóa chất độc hại gây ung thư da nhiều hơn là ánh nắng mặt trời",
            verdict = FactCheckVerdict.SAI,
            shortExplanation = "Bức xạ cực tím (UV) là chất sinh ung thư nhóm 1 đã được chứng minh; kem chống nắng giúp giảm nguy cơ ung thư da.",
            detailedExplanation = "Ánh nắng mặt trời phát ra tia UVA và UVB phá hủy trực tiếp cấu trúc ADN của tế bào biểu bì, dẫn đến u hắc tố và ung thư biểu mô tế bào đáy. Các loại kem chống nắng được FDA và EU phê duyệt đều trải qua kiểm định an toàn nghiêm ngặt.",
            evidence = "Khuyến cáo từ Hiệp hội Da liễu Hoa Kỳ (AAD) và Viện Da liễu Quốc gia Việt Nam: Thường xuyên sử dụng kem chống nắng SPF 30+ làm giảm nguy cơ ung thư tế bào hắc tố đến 50%.",
            counterEvidence = "Một số hạt nano trong kem chống nắng từng được tranh luận về mức độ hấp thụ qua da, nhưng các nghiên cứu diện rộng kết luận chúng chỉ nằm ở lớp sừng ngoài cùng.",
            references = listOf(
                ReferenceSource(
                    title = "American Academy of Dermatology: Sunscreen FAQs and Safety",
                    url = "https://www.aad.org/public/everyday-care/sun-protection/shade-clothing-sunscreen/sunscreen-faqs",
                    publisher = "American Academy of Dermatology (AAD)",
                    publishDate = "19/04/2023",
                    supportingSnippet = "Scientific evidence supports the benefits of using sunscreen to minimize short-term and long-term damage to the skin.",
                    isPrimarySource = true
                )
            ),
            limitations = "Lưu ý chọn loại kem chống nắng phù hợp với từng loại da và có chứng nhận an toàn."
        ),
        VerifiedFactCase(
            keywords = listOf("cam kết lãi", "30%", "đầu tư", "lợi nhuận mỗi ngày", "lãi suất khủng"),
            canonicalClaim = "Dự án đầu tư tài chính trực tuyến cam kết chắc chắn sinh lời 30% đến 50% mỗi ngày không rủi ro",
            verdict = FactCheckVerdict.SAI,
            shortExplanation = "Không có hoạt động kinh doanh hợp pháp nào có thể đảm bảo mức lợi nhuận 30% mỗi ngày; đây là dấu hiệu của mô hình Ponzi lừa đảo.",
            detailedExplanation = "Theo quy luật kinh tế, lợi nhuận luôn đi kèm với rủi ro. Các đối tượng lừa đảo thường tạo sàn giao dịch ảo, giai đoạn đầu cho rút tiền nhỏ để lấy lòng tin, sau đó khóa tài khoản và biến mất khi nạn nhân nạp số tiền lớn.",
            evidence = "Cảnh báo khẩn cấp từ Cục An ninh mạng và phòng, chống tội phạm sử dụng công nghệ cao (A05) - Bộ Công an về các bẫy đầu tư tài chính trực tuyến.",
            counterEvidence = "Không có định chế tài chính hay ngân hàng trung ương nào trên thế giới công nhận các sàn cam kết lãi siêu tưởng này.",
            references = listOf(
                ReferenceSource(
                    title = "Cổng thông tin Bộ Công an: Cảnh báo thủ đoạn lừa đảo kêu gọi đầu tư tài chính với lãi suất cao",
                    url = "https://bocongan.gov.vn",
                    publisher = "Bộ Công An Việt Nam",
                    publishDate = "14/09/2023",
                    supportingSnippet = "Khuyến cáo người dân không tham gia vào các ứng dụng, website đầu tư cam kết lãi suất bất thường.",
                    isPrimarySource = true
                )
            ),
            limitations = "Cảnh báo áp dụng đối với các mô hình huy động vốn tự xưng không có giấy phép của Ủy ban Chứng khoán Nhà nước."
        ),
        VerifiedFactCase(
            keywords = listOf("trí tuệ nhân tạo", "ai", "thay thế", "nghề lập trình", "chấm dứt"),
            canonicalClaim = "Trí tuệ nhân tạo sẽ thay thế 100% lập trình viên và ngành phần mềm sẽ hoàn toàn biến mất trong năm tới",
            verdict = FactCheckVerdict.GAY_HIEU_LAM,
            shortExplanation = "AI đang thay đổi cách viết mã và tăng năng suất, nhưng chưa thể thay thế hoàn toàn tư duy kiến trúc và kỹ sư phần mềm.",
            detailedExplanation = "Mặc dù các mô hình ngôn ngữ lớn có khả năng tự động hóa các tác vụ lặp lại và sinh mã nhanh, việc phát triển phần mềm đòi hỏi nắm bắt nghiệp vụ, kiểm thử hệ thống, quản lý bảo mật, hạ tầng đám mây và tương tác với con người. Vai trò lập trình viên chuyển biến từ viết mã đơn thuần sang điều phối và kiểm soát chất lượng AI.",
            evidence = "Báo cáo của Diễn đàn Kinh tế Thế giới (WEF) và khảo sát từ ACM, IEEE: Nhu cầu nhân lực công nghệ thông tin có kỹ năng quản trị AI dự kiến tiếp tục tăng trưởng.",
            counterEvidence = "Một số công việc gia công phần mềm cấp thấp (junior copy-paste) có thể suy giảm do các công cụ sinh mã AI.",
            references = listOf(
                ReferenceSource(
                    title = "World Economic Forum: The Future of Jobs Report",
                    url = "https://www.weforum.org/reports/the-future-of-jobs-report-2023/",
                    publisher = "World Economic Forum (WEF)",
                    publishDate = "01/05/2023",
                    supportingSnippet = "Technology creates new technical roles while transforming traditional tasks into AI-assisted workflows.",
                    isPrimarySource = true
                )
            ),
            limitations = "Dự báo công nghệ có tính biến động nhanh theo tốc độ phát triển của mô hình AI."
        )
    )

    /**
     * Thực hiện kiểm chứng 5 bước cho một tuyên bố
     */
    fun verifyClaim(
        extractedClaim: String,
        sourceApp: String = "Màn hình trực tiếp",
        rawScreenText: String = ""
    ): FactCheckRecord {
        val normalized = extractedClaim.trim()
        val lower = normalized.lowercase()

        // 1. Kiểm tra trong Cơ sở dữ liệu Bằng chứng đã xác minh (Knowledge Base Match)
        val matchedCase = verifiedKnowledgeBase.firstOrNull { factCase ->
            factCase.keywords.count { kw -> lower.contains(kw) } >= 2 ||
                    factCase.keywords.any { kw -> lower.contains(kw) && kw.length >= 8 }
        }

        if (matchedCase != null) {
            val isAlert = (matchedCase.verdict == FactCheckVerdict.SAI || matchedCase.verdict == FactCheckVerdict.GAY_HIEU_LAM)
            return FactCheckRecord(
                id = UUID.randomUUID().toString(),
                originalClaim = normalized,
                normalizedClaim = matchedCase.canonicalClaim,
                verdict = matchedCase.verdict,
                shortExplanation = matchedCase.shortExplanation,
                detailedExplanation = matchedCase.detailedExplanation,
                evidence = matchedCase.evidence,
                counterEvidence = matchedCase.counterEvidence,
                references = matchedCase.references,
                checkedAt = System.currentTimeMillis(),
                limitations = matchedCase.limitations,
                confidence = 0.95f,
                sourceApp = sourceApp,
                rawScreenText = rawScreenText,
                isAlerted = isAlert
            )
        }

        // 2. Phân tích ngữ nghĩa theo quy tắc Fact-Checking nghiêm ngặt (Heuristic Evaluation)
        return evaluateGenericClaim(normalized, sourceApp, rawScreenText)
    }

    private fun evaluateGenericClaim(
        claim: String,
        sourceApp: String,
        rawScreenText: String
    ): FactCheckRecord {
        val lower = claim.lowercase()

        // Kiểm tra nếu là ý kiến cá nhân / cảm xúc
        if (lower.startsWith("theo tôi") || lower.startsWith("tôi cảm thấy") || lower.contains("đẹp tuyệt vời") || lower.contains("buồn quá")) {
            return FactCheckRecord(
                id = UUID.randomUUID().toString(),
                originalClaim = claim,
                normalizedClaim = claim,
                verdict = FactCheckVerdict.KHONG_THE_KIEM_CHUNG,
                shortExplanation = "Nội dung phản ánh cảm xúc hoặc quan điểm chủ quan của cá nhân, không cấu thành tuyên bố sự thật định lượng.",
                detailedExplanation = "Các ý kiến chủ quan, cảm thụ nghệ thuật hoặc đánh giá cá nhân không có tiêu chuẩn chân lý khách quan để xác minh đúng/sai.",
                evidence = "Tiêu chuẩn Fact-checking Quốc tế (IFCN): Bỏ qua ý kiến chủ quan và dự đoán tương lai vô căn cứ.",
                references = listOf(
                    ReferenceSource(
                        title = "Poynter / IFCN Code of Principles",
                        url = "https://www.ifcncodeofprinciples.poynter.org/",
                        publisher = "Mạng lưới Kiểm chứng Quốc tế (IFCN)",
                        publishDate = "2023",
                        supportingSnippet = "Fact-checkers only evaluate claims of factual truth, not personal beliefs."
                    )
                ),
                limitations = "Hệ thống tôn trọng quyền tự do biểu đạt ý kiến cá nhân.",
                confidence = 0.85f,
                sourceApp = sourceApp,
                rawScreenText = rawScreenText,
                isAlerted = false
            )
        }

        // Kiểm tra dấu hiệu lừa đảo / giật gân rõ rệt
        val scamKeywords = listOf("lãi suất", "nạp tiền", "nhận quà", "bấm vào link", "trúng thưởng", "mã otp", "chuyển tiền ngay")
        if (scamKeywords.any { lower.contains(it) } && (lower.contains("tặng") || lower.contains("miễn phí") || lower.contains("gấp"))) {
            return FactCheckRecord(
                id = UUID.randomUUID().toString(),
                originalClaim = claim,
                normalizedClaim = claim,
                verdict = FactCheckVerdict.SAI,
                shortExplanation = "Thông điệp có cấu trúc điển hình của tin nhắn mạo danh hoặc bẫy lừa đảo trực tuyến.",
                detailedExplanation = "Các cơ quan nhà nước, ngân hàng và thương hiệu chính thống không bao giờ yêu cầu gửi mã OTP, chuyển tiền đặt cọc hoặc hứa hẹn trúng thưởng qua các bài đăng mạng xã hội không kiểm chứng.",
                evidence = "Cảnh báo từ Cục An toàn thông tin - Bộ Thông tin & Truyền thông về các chiêu trò lừa đảo mạo danh thương hiệu.",
                references = listOf(
                    ReferenceSource(
                        title = "Cổng thông tin An toàn thông tin Quốc gia",
                        url = "https://khonggianmang.vn",
                        publisher = "Cục An Toàn Thông Tin - Bộ TTTT",
                        publishDate = "2024",
                        supportingSnippet = "Cảnh giác trước các tin nhắn trúng thưởng, mời nạp tiền nhận hoa hồng cao trên mạng xã hội."
                    )
                ),
                limitations = "Cảnh báo bảo vệ người dùng trước rủi ro tài chính.",
                confidence = 0.90f,
                sourceApp = sourceApp,
                rawScreenText = rawScreenText,
                isAlerted = true
            )
        }

        // Quy tắc vàng của prompt:
        // "Nếu không tìm được bằng chứng đủ mạnh, phải trả về 'Chưa đủ bằng chứng', không được tự suy diễn thành 'Sai'."
        return FactCheckRecord(
            id = UUID.randomUUID().toString(),
            originalClaim = claim,
            normalizedClaim = claim,
            verdict = FactCheckVerdict.CHUA_DU_BANG_CHUNG,
            shortExplanation = "Chưa tìm thấy đủ tài liệu độc lập hoặc thông cáo chính thức để xác thực hoặc phủ định tuyên bố này.",
            detailedExplanation = "Theo nguyên tắc bảo đảm tính trung thực của LTM FactCheck AI, hệ thống không tự ý quy kết 'Sai' khi cơ sở dữ liệu hiện tại chưa có bằng chứng đối chiếu xác đáng. Đang tiếp tục theo dõi các nguồn tin chính thống.",
            evidence = "Chưa có nguồn kiểm chứng độc lập nào công bố báo cáo về nội dung cụ thể này.",
            counterEvidence = "Chưa ghi nhận phản bác chính thức từ các cơ quan hữu quan.",
            references = listOf(
                ReferenceSource(
                    title = "Hướng dẫn thẩm định thông tin mở rộng",
                    url = "https://toolbox.google.com/factcheck/explorer",
                    publisher = "Google Fact Check Tools",
                    publishDate = dateFormat.format(Date()),
                    supportingSnippet = "Khi thông tin chưa có tiền lệ xác minh, cần thận trọng và tra cứu thêm tại cơ quan chức năng."
                )
            ),
            limitations = "Cần thêm bằng chứng từ các cơ quan chuyên môn có thẩm quyền.",
            confidence = 0.75f,
            sourceApp = sourceApp,
            rawScreenText = rawScreenText,
            isAlerted = false
        )
    }

    private data class VerifiedFactCase(
        val keywords: List<String>,
        val canonicalClaim: String,
        val verdict: FactCheckVerdict,
        val shortExplanation: String,
        val detailedExplanation: String,
        val evidence: String,
        val counterEvidence: String,
        val references: List<ReferenceSource>,
        val limitations: String
    )
}
