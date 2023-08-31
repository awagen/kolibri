

class Modal {

    showModal: boolean
    modalTitle: string
    mainContent: string
    footerContent: string
    mode: string


    constructor(showModal: boolean = false,
                modalTitle: string = "",
                mainContent: string = "",
                footerContent: string = "",
                mode: string = "k-success") {
        this.showModal = showModal
        this.modalTitle = modalTitle
        this.mainContent = mainContent
        this.footerContent = footerContent
        this.mode = mode
    }

    hide() {
        this.showModal = false
    }

    show() {
        this.showModal = true
    }

    reset() {
        this.modalTitle = ""
        this.mainContent = ""
        this.footerContent = ""
        this.mode = "k-success"
    }

    prepareOKResponseShow() {
        this.reset()
        this.mode = "k-success"
    }

    prepareOKResponseShowAndShow() {
        this.prepareOKResponseShow()
        this.show()
    }

    prepareErrorResponseShow(title, description) {
        this.reset()
        this.modalTitle = title
        this.mainContent = description
        this.mode = "k-fail"
    }

    prepareErrorResponseShowAndShow(title, description) {
        this.prepareErrorResponseShow(title, description)
        this.show()
    }

}

export {Modal}