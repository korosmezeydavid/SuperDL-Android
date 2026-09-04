// Package whmobile: vekony reteg a wormhole-william (MIT) konyvtar folott,
// gomobile-lal Android AAR-ra forditva.
//
// MIERT KELL: a windowsos SuperDL p2p modulja a magic-wormhole protokollal
// kuld fajlt gepre. Androidon nincs parancssori wormhole, amit alfolyamatkent
// lehetne hajtani, ezert a protokollt egy beforditott konyvtar adja. Ugyanaz
// a halo, ugyanaz a kod-szotar, tehat a telefon es a gep egymasnak kuldhet.
//
// FIGYELEM: ebben a fajlban NINCS ekezet. A gomobile es a Go eszkozlanc
// Windowson kulonbozo kodlapokkal dolgozik, es az ekezetes forras nemely
// lancszemnel elszall. A felhasznalonak szant magyar mondatok a Kotlin
// oldalon vannak, ott a helyuk.
package whmobile

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"

	"github.com/psanford/wormhole-william/wormhole"
)

// Callback: a Kotlin oldal valositja meg. A gomobile ebbol Java interfeszt
// general.
type Callback interface {
	OnCode(code string)
	OnProgress(pct int)
	OnDone(ok bool, message string)
}

var (
	mu     sync.Mutex
	cancel context.CancelFunc
)

// Cancel: a folyamatban levo kuldes vagy fogadas megszakitasa.
func Cancel() {
	mu.Lock()
	defer mu.Unlock()
	if cancel != nil {
		cancel()
		cancel = nil
	}
}

func newContext() context.Context {
	mu.Lock()
	defer mu.Unlock()
	if cancel != nil {
		cancel()
	}
	ctx, c := context.WithCancel(context.Background())
	cancel = c
	return ctx
}

func progressFunc(cb Callback) func(int64, int64) {
	last := -1
	return func(sent int64, total int64) {
		if total <= 0 {
			return
		}
		pct := int(sent * 100 / total)
		if pct < 0 {
			pct = 0
		}
		if pct > 100 {
			pct = 100
		}
		if pct != last {
			last = pct
			cb.OnProgress(pct)
		}
	}
}

// Send: egy fajl elkuldese. A kod azonnal megjon (OnCode), a fajl akkor megy
// at, amikor a masik oldal beirja.
func Send(path string, cb Callback) {
	go func() {
		defer func() {
			if r := recover(); r != nil {
				cb.OnDone(false, fmt.Sprintf("belso hiba: %v", r))
			}
		}()
		f, err := os.Open(path)
		if err != nil {
			cb.OnDone(false, "a fajl nem nyithato meg: "+err.Error())
			return
		}
		defer f.Close()

		var c wormhole.Client
		ctx := newContext()
		code, status, err := c.SendFile(ctx, filepath.Base(path), f,
			wormhole.WithProgress(progressFunc(cb)))
		if err != nil {
			cb.OnDone(false, "a kuldes nem indult el: "+err.Error())
			return
		}
		cb.OnCode(code)

		s := <-status
		if s.Error != nil {
			cb.OnDone(false, s.Error.Error())
			return
		}
		if !s.OK {
			cb.OnDone(false, "a masik oldal nem fogadta el a fajlt")
			return
		}
		cb.OnDone(true, "")
	}()
}

// Receive: fajl fogadasa a megadott koddal, a megadott mappaba.
//
// A VEGEN ELLENORZUNK. A windowsos modul tanulsaga: nem eleg, hogy a
// muvelet hibatlanul lefutott — a fajlnak OTT KELL LENNIE, es nem lehet
// nulla meretu. Virusirto, lemezhiba vagy reszleges iras utan is
// "sikeres" latszat keletkezhetne.
func Receive(code string, outDir string, cb Callback) {
	go func() {
		defer func() {
			if r := recover(); r != nil {
				cb.OnDone(false, fmt.Sprintf("belso hiba: %v", r))
			}
		}()
		if err := os.MkdirAll(outDir, 0o755); err != nil {
			cb.OnDone(false, "a celmappa nem hozhato letre: "+err.Error())
			return
		}
		var c wormhole.Client
		ctx := newContext()
		msg, err := c.Receive(ctx, code)
		if err != nil {
			cb.OnDone(false, err.Error())
			return
		}
		name := filepath.Base(msg.Name)
		if name == "" || name == "." || name == string(os.PathSeparator) {
			name = "fogadott_fajl"
		}
		target := uniquePath(outDir, name)
		out, err := os.Create(target)
		if err != nil {
			cb.OnDone(false, "a fajl nem irhato: "+err.Error())
			return
		}
		n, err := io.Copy(out, &progressReader{r: msg, total: msg.TransferBytes64, cb: cb})
		closeErr := out.Close()
		if err != nil {
			cb.OnDone(false, "az atvitel megszakadt: "+err.Error())
			return
		}
		if closeErr != nil {
			cb.OnDone(false, "a fajl lezarasa nem sikerult: "+closeErr.Error())
			return
		}
		st, statErr := os.Stat(target)
		if statErr != nil || st.Size() <= 0 || n <= 0 {
			cb.OnDone(false, "a fajl nem talalhato a celmappaban")
			return
		}
		cb.OnDone(true, target)
	}()
}

// progressReader: a fogadas halado jelzese. A wormhole-william a kuldesre ad
// beepitett progress-t, a fogadasra nem — ott az olvasast szamoljuk.
type progressReader struct {
	r     io.Reader
	total int64
	read  int64
	last  int
	cb    Callback
}

func (p *progressReader) Read(b []byte) (int, error) {
	n, err := p.r.Read(b)
	p.read += int64(n)
	if p.total > 0 {
		pct := int(p.read * 100 / p.total)
		if pct > 100 {
			pct = 100
		}
		if pct != p.last {
			p.last = pct
			p.cb.OnProgress(pct)
		}
	}
	return n, err
}

// uniquePath: sose irjunk felul egy meglevo fajlt. Vakon egy csendben
// felulirt fajl visszaszerezhetetlen.
func uniquePath(dir string, name string) string {
	target := filepath.Join(dir, name)
	if _, err := os.Stat(target); os.IsNotExist(err) {
		return target
	}
	ext := filepath.Ext(name)
	base := name[:len(name)-len(ext)]
	for i := 2; i < 1000; i++ {
		cand := filepath.Join(dir, fmt.Sprintf("%s (%d)%s", base, i, ext))
		if _, err := os.Stat(cand); os.IsNotExist(err) {
			return cand
		}
	}
	return filepath.Join(dir, fmt.Sprintf("%s (%d)%s", base, os.Getpid(), ext))
}
